import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Disposable dedicated-server probe for the RPG Series spell patches (D3 P1 cooldown haste, D9 apply limits).
 * Spell Engine and Spell Power are not on lampas2-overrides' compile classpath, so their members are reached by
 * reflection; each was checked with javap against spell_engine-fabric-1.10.9+26.2.jar and
 * spell_power-fabric-1.6.2+26.2.jar. Reads targets.json (written by run.py) and writes rpg-spell-patch-probe.json.
 */
public final class RpgSpellPatchProbe implements ModInitializer {

	private static boolean probed;

	/**
	 * Runs on the first server tick: Spell Engine fills StatusEffectClassification's movement-impairing set from its
	 * own SERVER_STARTED listener, so a SERVER_STARTED probe could read it before it is populated.
	 */
	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!probed) {
				probed = true;
				probe(server);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static void probe(MinecraftServer server) {
		JsonObject result = new JsonObject();
		try {
			JsonObject targets = JsonParser.parseString(Files.readString(Path.of("targets.json"))).getAsJsonObject();
			Class<?> spellRegistry = Class.forName("net.spell_engine.api.spell.registry.SpellRegistry");
			ResourceKey<Registry<Object>> key = (ResourceKey<Registry<Object>>) spellRegistry.getField("KEY").get(null);
			Registry<Object> registry = server.registryAccess().lookupOrThrow(key);
			result.addProperty("registry", String.valueOf(key));
			result.addProperty("registry_size", registry.size());

			Class<?> params = Class.forName("net.spell_engine.internals.SpellParameters");
			Method cooldown = params.getMethod("getCooldownDuration", LivingEntity.class, Holder.class);
			Class<?> schoolClass = Class.forName("net.spell_power.api.SpellSchool");
			Method haste = Class.forName("net.spell_power.api.SpellPower").getMethod("getHaste", LivingEntity.class, schoolClass);
			Class<?> resultClass = Class.forName("net.spell_power.api.SpellPower$Result");
			Constructor<?> newResult = resultClass.getConstructor(schoolClass, double.class, double.class, double.class);
			Class<?> limitClass = Class.forName("net.spell_engine.api.spell.Spell$Impact$Action$StatusEffect$ApplyLimit");
			Method underLimit = Class.forName("net.spell_engine.internals.impact.SpellImpacts")
				.getMethod("underApplyLimit", resultClass, LivingEntity.class, schoolClass, limitClass);
			Class<?> config = Class.forName("net.spell_engine.SpellEngineMod");
			Object serverConfig = config.getField("config").get(null);
			result.addProperty("haste_affects_cooldown", (boolean) field(serverConfig, "haste_affects_cooldown"));

			// Haste on every school the patches touch: attack speed x2 (physical melee), ranged haste +100
			// (physical ranged) and spell haste +100 (casters and the physical-school spell haste term).
			FakePlayer player = FakePlayer.get(server.overworld());
			modifier(player, "minecraft:attack_speed", 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			modifier(player, "ranged_weapon:haste", 100.0, AttributeModifier.Operation.ADD_VALUE);
			modifier(player, "spell_power:haste", 100.0, AttributeModifier.Operation.ADD_VALUE);

			JsonArray spells = new JsonArray();
			for (var element : targets.getAsJsonArray("spells")) {
				JsonObject target = element.getAsJsonObject();
				Identifier id = Identifier.parse(target.get("id").getAsString());
				JsonObject out = target.deepCopy();
				out.remove("checks");
				Optional<? extends Holder<Object>> holder = registry.get(id);
				out.addProperty("registered", holder.isPresent());
				if (holder.isEmpty()) {
					spells.add(out);
					continue;
				}
				Object spell = holder.get().value();
				Object school = field(spell, "school");
				Object cd = field(field(spell, "cost"), "cooldown");
				out.addProperty("school", String.valueOf(field(school, "id")));
				out.addProperty("cooldown_duration", (float) field(cd, "duration"));
				out.addProperty("haste_affected", (boolean) field(cd, "haste_affected"));
				out.addProperty("haste", (float) haste.invoke(null, player, school));
				out.addProperty("effective_cooldown", (float) cooldown.invoke(null, player, holder.get()));
				if (target.has("impact")) {
					List<?> impacts = (List<?>) field(spell, "impacts");
					Object statusEffect = field(field(impacts.get(target.get("impact").getAsInt()), "action"), "status_effect");
					Object limit = field(statusEffect, "apply_limit");
					out.addProperty("effect_id", (String) field(statusEffect, "effect_id"));
					out.addProperty("health_base", (float) field(limit, "health_base"));
					out.addProperty("spell_power_multiplier", (float) field(limit, "spell_power_multiplier"));
					out.add("effect", effectClass(statusEffect));
					JsonArray checks = new JsonArray();
					for (var check : target.getAsJsonArray("checks")) {
						JsonObject c = check.getAsJsonObject().deepCopy();
						LivingEntity zombie = EntityTypes.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
						zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(c.get("max_health").getAsDouble());
						c.addProperty("target_max_health", zombie.getMaxHealth());
						Object power = newResult.newInstance(school, c.get("power").getAsDouble(), 0.0, 0.0);
						c.addProperty("applies", (boolean) underLimit.invoke(null, power, zombie, school, limit));
						checks.add(c);
						zombie.discard();
					}
					out.add("limit_checks", checks);
				}
				out.add("resource", resource(server, id));
				spells.add(out);
			}
			result.add("spells", spells);
			result.addProperty("status", "COMPLETE");
		} catch (Throwable t) {
			result.addProperty("status", "ERROR");
			result.addProperty("error", t.toString());
			t.printStackTrace();
		}
		try {
			Files.writeString(Path.of("rpg-spell-patch-probe.json"),
				new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		System.out.println("RPG_SPELL_PATCH_PROBE " + result.get("status").getAsString());
	}

	/** Bytes the resource manager serves on the lookup and listing paths, with the source pack. */
	private static JsonObject resource(MinecraftServer server, Identifier spellId) throws Exception {
		Identifier file = Identifier.fromNamespaceAndPath(spellId.getNamespace(), "spell/" + spellId.getPath() + ".json");
		JsonObject out = new JsonObject();
		Optional<Resource> top = server.getResourceManager().getResource(file);
		if (top.isPresent()) {
			out.addProperty("get_resource_sha256", sha256(top.get()));
			out.addProperty("get_resource_pack", top.get().sourcePackId());
		}
		Map<Identifier, Resource> listed = server.getResourceManager().listResources("spell", id -> id.equals(file));
		if (listed.containsKey(file)) {
			out.addProperty("list_resources_sha256", sha256(listed.get(file)));
			out.addProperty("list_resources_pack", listed.get(file).sourcePackId());
		}
		return out;
	}

	/** Whether Spell Engine's boss gate refuses this effect on #spell_engine:bosses regardless of the limit. */
	private static JsonObject effectClass(Object statusEffect) throws Exception {
		JsonObject out = new JsonObject();
		String effectId = (String) field(statusEffect, "effect_id");
		Optional<? extends Holder<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId));
		out.addProperty("registered", effect.isPresent());
		if (effect.isPresent()) {
			Class<?> classification = Class.forName("net.spell_engine.api.effect.StatusEffectClassification");
			out.addProperty("movement_impairing",
				(boolean) classification.getMethod("isMovementImpairing", Holder.class).invoke(null, effect.get()));
			out.addProperty("disables_mob_ai",
				(boolean) classification.getMethod("disablesMobAI", Holder.class).invoke(null, effect.get()));
		}
		return out;
	}

	private static void modifier(LivingEntity entity, String attribute, double amount, AttributeModifier.Operation operation) {
		Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(attribute)).orElseThrow();
		entity.getAttribute(holder).addPermanentModifier(
			new AttributeModifier(Identifier.fromNamespaceAndPath("lampas2_probe", "haste"), amount, operation));
	}

	private static Object field(Object owner, String name) throws ReflectiveOperationException {
		return owner.getClass().getField(name).get(owner);
	}

	private static String sha256(Resource resource) throws Exception {
		try (InputStream in = resource.open()) {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
		}
	}
}
