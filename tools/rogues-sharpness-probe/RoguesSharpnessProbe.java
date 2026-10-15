import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

/**
 * Disposable dedicated-server probe: records how the running server resolved and decoded Sharpness.
 * Writes rogues-sharpness-probe.json into the server directory once the server has started.
 */
public final class RoguesSharpnessProbe implements ModInitializer {
	private static final Identifier SHARPNESS_FILE = Identifier.withDefaultNamespace("enchantment/sharpness.json");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(RoguesSharpnessProbe::probe);
	}

	private static void probe(MinecraftServer server) {
		JsonObject result = new JsonObject();
		try {
			result.add("decoded", decoded(server));
			result.add("resource_manager", resourceManager(server));
			result.add("rogues_pack", roguesPack(server));
			result.addProperty("status", "COMPLETE");
		} catch (Throwable t) {
			result.addProperty("status", "ERROR");
			result.addProperty("error", t.toString());
		}
		try {
			Files.writeString(Path.of("rogues-sharpness-probe.json"),
				new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println("ROGUES_SHARPNESS_PROBE " + result.get("status").getAsString());
	}

	/** The Sharpness entry the registry loader decoded, re-encoded with the vanilla codec. */
	private static JsonObject decoded(MinecraftServer server) {
		HolderLookup.RegistryLookup<Enchantment> enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);
		Holder<Enchantment> smite = enchantments.getOrThrow(Enchantments.SMITE);
		Holder<Enchantment> bane = enchantments.getOrThrow(Enchantments.BANE_OF_ARTHROPODS);
		Enchantment value = sharpness.value();

		JsonObject out = new JsonObject();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		out.add("encoded", Enchantment.DIRECT_CODEC.encodeStart(ops, value).getOrThrow());

		List<ConditionalEffect<EnchantmentValueEffect>> damage = value.getEffects(EnchantmentEffectComponents.DAMAGE);
		out.addProperty("damage_effect_count", damage.size());
		out.addProperty("has_attribute_effects", value.effects().has(EnchantmentEffectComponents.ATTRIBUTES));
		JsonObject bonus = new JsonObject();
		for (int level = 1; level <= value.getMaxLevel(); level++) {
			float added = 0.0F;
			for (ConditionalEffect<EnchantmentValueEffect> effect : damage) {
				added = effect.effect().process(level, RandomSource.create(0L), added);
			}
			bonus.addProperty(Integer.toString(level), added);
		}
		out.add("flat_damage_bonus_by_level", bonus);
		out.addProperty("max_level", value.getMaxLevel());

		out.addProperty("exclusive_set_tag",
			value.exclusiveSet().unwrapKey().map(key -> "#" + key.location()).orElse("<direct>"));
		JsonArray members = new JsonArray();
		value.exclusiveSet().stream().forEach(holder -> members.add(holder.getRegisteredName()));
		out.add("exclusive_set_members", members);
		out.addProperty("compatible_with_smite", Enchantment.areCompatible(sharpness, smite));
		out.addProperty("compatible_with_bane_of_arthropods", Enchantment.areCompatible(sharpness, bane));
		out.addProperty("smite_compatible_with_bane_of_arthropods", Enchantment.areCompatible(smite, bane));
		return out;
	}

	/** What the server resource manager returns on its lookup and listing paths. */
	private static JsonObject resourceManager(MinecraftServer server) throws IOException {
		JsonObject out = new JsonObject();
		Optional<Resource> top = server.getResourceManager().getResource(SHARPNESS_FILE);
		out.add("get_resource", top.isPresent() ? describe(top.get()) : null);
		Map<Identifier, Resource> listed = server.getResourceManager()
			.listResources("enchantment", id -> id.equals(SHARPNESS_FILE));
		Resource fromList = listed.get(SHARPNESS_FILE);
		out.add("list_resources", fromList != null ? describe(fromList) : null);
		JsonArray stack = new JsonArray();
		for (Resource resource : server.getResourceManager().getResourceStack(SHARPNESS_FILE)) {
			stack.add(describe(resource));
		}
		out.add("resource_stack", stack);
		JsonArray packs = new JsonArray();
		server.getResourceManager().listPacks().forEach(pack -> packs.add(pack.packId()));
		out.add("pack_order", packs);
		return out;
	}

	/** Rogues' own pack, queried directly on both PackResources entry points. */
	private static JsonObject roguesPack(MinecraftServer server) throws IOException {
		JsonObject out = new JsonObject();
		Optional<PackResources> rogues = server.getResourceManager().listPacks()
			.filter(pack -> pack.packId().equals("rogues")).findFirst();
		out.addProperty("found", rogues.isPresent());
		if (rogues.isEmpty()) {
			return out;
		}
		PackResources pack = rogues.get();
		out.addProperty("class", pack.getClass().getName());
		IoSupplier<InputStream> direct = pack.getResource(PackType.SERVER_DATA, SHARPNESS_FILE);
		out.add("get_resource", direct != null ? describe(direct) : null);
		AtomicReference<IoSupplier<InputStream>> listed = new AtomicReference<>();
		pack.listResources(PackType.SERVER_DATA, "minecraft", "enchantment", (id, supplier) -> {
			if (id.equals(SHARPNESS_FILE)) {
				listed.set(supplier);
			}
		});
		out.add("list_resources", listed.get() != null ? describe(listed.get()) : null);
		return out;
	}

	private static JsonObject describe(Resource resource) throws IOException {
		JsonObject out = describe(resource::open);
		out.addProperty("source_pack", resource.sourcePackId());
		return out;
	}

	private static JsonObject describe(IoSupplier<InputStream> supplier) throws IOException {
		byte[] bytes;
		try (InputStream in = supplier.get()) {
			bytes = in.readAllBytes();
		}
		JsonObject out = new JsonObject();
		out.addProperty("sha256", sha256(bytes));
		JsonObject json = com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		out.add("effect_types", json.getAsJsonObject("effects").keySet().stream()
			.collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
		out.add("exclusive_set", json.get("exclusive_set"));
		return out;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
