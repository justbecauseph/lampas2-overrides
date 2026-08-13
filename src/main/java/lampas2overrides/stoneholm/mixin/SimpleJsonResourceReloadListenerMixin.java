package lampas2overrides.stoneholm.mixin;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

/** Repairs Stoneholm loot data after JSON parsing but before registry-aware codec validation. */
@Mixin(SimpleJsonResourceReloadListener.class)
abstract class SimpleJsonResourceReloadListenerMixin {

	@Unique
	private static final Logger LAMPAS2_LOGGER =
		LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/stoneholm");

	@Unique
	private static final Set<String> CREATE_ONLY_LOOT_TABLES = Set.of(
		"stoneholm:andesite_worker",
		"stoneholm:brass_worker",
		"stoneholm:copper_worker"
	);

	@Unique
	private static final Pattern LEGACY_POTION =
		Pattern.compile("\\{Potion:\\s*\\\"([^\\\"]+)\\\"}");

	@Unique
	private static final JsonElement EMPTY_CHEST_LOOT_TABLE =
		JsonParser.parseString("{\"type\":\"minecraft:chest\",\"pools\":[]}");

	@ModifyExpressionValue(
		method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"
		)
	)
	private static JsonElement lampas2$repairStoneholmLoot(JsonElement parsed, @Local(name = "id") Identifier id) {
		String resourceId = id.toString();
		if (CREATE_ONLY_LOOT_TABLES.contains(resourceId)
			&& !FabricLoader.getInstance().isModLoaded("create")) {
			LAMPAS2_LOGGER.info("Suppressing Create-only loot table {} because Create is absent", resourceId);
			return EMPTY_CHEST_LOOT_TABLE.deepCopy();
		}

		if ("stoneholm:cleric".equals(resourceId) && lampas2$upgradePotionFunctions(parsed)) {
			LAMPAS2_LOGGER.info("Upgraded legacy potion functions in Stoneholm's cleric loot table");
		}

		return parsed;
	}

	@Unique
	private static boolean lampas2$upgradePotionFunctions(JsonElement parsed) {
		if (!parsed.isJsonObject()) {
			return false;
		}

		boolean changed = false;
		JsonArray pools = parsed.getAsJsonObject().getAsJsonArray("pools");
		if (pools == null) {
			return false;
		}

		for (JsonElement poolElement : pools) {
			JsonArray entries = poolElement.getAsJsonObject().getAsJsonArray("entries");
			if (entries == null) {
				continue;
			}

			for (JsonElement entryElement : entries) {
				JsonArray functions = entryElement.getAsJsonObject().getAsJsonArray("functions");
				if (functions == null) {
					continue;
				}

				for (JsonElement functionElement : functions) {
					JsonObject function = functionElement.getAsJsonObject();
					if (!"minecraft:set_nbt".equals(function.get("function").getAsString()) || !function.has("tag")) {
						continue;
					}

					Matcher matcher = LEGACY_POTION.matcher(function.get("tag").getAsString());
					if (!matcher.matches()) {
						continue;
					}

					function.addProperty("function", "minecraft:set_potion");
					function.addProperty("id", matcher.group(1));
					function.remove("tag");
					changed = true;
				}
			}
		}

		return changed;
	}
}
