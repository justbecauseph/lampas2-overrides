package lampas2overrides.incendium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import net.minecraft.advancements.predicates.NbtPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.CustomData;

public class IncendiumCompatibilityTest {

	@Test
	void supportsOnlyVerifiedIncendiumVersions() {
		assertFalse(IncendiumCompatibility.supportsVersion("5.5.0"));
		assertTrue(IncendiumCompatibility.supportsVersion("5.5.1"));
		assertFalse(IncendiumCompatibility.supportsVersion("5.5.2"));
		assertFalse(IncendiumCompatibility.supportsVersion("5.6.0"));
		assertFalse(IncendiumCompatibility.supportsVersion("5.5.0+modified"));
		assertFalse(IncendiumCompatibility.supportsVersion("5.5.1+modified"));
		assertFalse(IncendiumCompatibility.supportsVersion(""));
		assertFalse(IncendiumCompatibility.supportsVersion("unknown"));
	}

	@Test
	void versionProfilesMapToCorrectPacks() {
		Optional<IncendiumCompatibility.Profile> p551 = IncendiumCompatibility.getProfile("5.5.1");
		assertTrue(p551.isPresent());
		assertEquals("5.5.1", p551.get().version());
		assertEquals(Identifier.fromNamespaceAndPath("lampas2-overrides", "incendium_5_5_1_optimizations"), p551.get().packId());
		Map<String, String> expected = new java.util.HashMap<>(Map.of(
				"data/incendium/function/clocks/main.mcfunction",
				"fe026903d2fae586c11320e263308c62b18eb9c0ae9fa11dcb1b7cabeba091dc",
				"data/incendium/function/player/main.mcfunction",
				"298e91e1b53a906c89fd326b3d8b6e1e64cc1c2b18ee914d28b1c0bd5e525518",
				"data/incendium/function/item/chilling_blade/on_hit.mcfunction",
				"dce837a919050bd97fe8b6f21fa10b21dc8bbaf42f4a864ecb232d02f896fca9",
				"data/incendium/function/entity/chilling.mcfunction",
				"d171b72e50e7737db20e14267f55ffcba0c14cdc16dac7c8cc8f171f525e337f",
				"data/incendium/function/technical/entity_id/check.mcfunction",
				"dbdbf31acb6d9274f63f218f2616e1f4883e0963909d40d362f00bf15f308c17",
				"data/incendium/function/technical/entity_id/reset.mcfunction",
				"340591a2b5a74330f04c9c0dfc07aa497302ef16c0f498cb07d6d0abe8dd92d5",
				"data/incendium/function/technical/summon_helper.mcfunction",
				"5ed3539c83a33c6c90fd7402fff2caac89c5277cc8f251dd3b99b5419e77b5f5"
		));
		expected.putAll(Map.of(
				"data/incendium/predicate/holding/voltaic_trident.json",
				"b2f765e09111ac713efafaa0f60e4dada2b2061aafe6a502b9908b3c7c0028a4",
				"data/incendium/predicate/holding/hefty_pickaxe.json",
				"b698e2e3d22a069c171ddb1b084f536cd7b2bcfd9ae2dde505e1ca936a1bc3dc",
				"data/incendium/predicate/wearing/scouts_battle_helm.json",
				"4544c23ccd67190c011ac484de86f6142bfb9e04f6c1d48d9c394463d8c54746",
				"data/incendium/predicate/holding/firestorm.json",
				"098539b8ba4a68c34f8135f3865b46593f2d55427a8abc3e16e7ba5fd863b457",
				"data/incendium/predicate/holding/firestorm/mainhand.json",
				"c0cf1ca8f6bdb60308b92141fcb001624a0b9c82559528ba0aba74c5dafcadfd",
				"data/incendium/predicate/holding/firestorm/offhand.json",
				"9ba0547a8b607cf7f8612aade7f3af1912760ac1c74962ca8e6c193ee763e8aa",
				"data/incendium/predicate/holding/holy_wrath.json",
				"26beaa6693b5c3c8bd01347be05b6d1399fea3a90e1e4d2ef43ae0b529eba4d2",
				"data/incendium/predicate/holding/sentrys_wrath.json",
				"45900f555d462e00d6ae8edf68db5b17a368d1e60adf780d3050856e4643da65",
				"data/incendium/predicate/holding/sentrys_wrath/mainhand.json",
				"eaa73249d97749db007ec78b36ce3717ba7ec047d61a1a22618245501133d8a3",
				"data/incendium/predicate/holding/sentrys_wrath/offhand.json",
				"d196ee4b53ad2601323c7f8104f7d0a1a6dbb67c57d41126536937de946d2e22"
		));
		assertEquals(expected, p551.get().expectedResources());

		assertTrue(IncendiumCompatibility.getProfile("5.5.0").isEmpty());
		assertTrue(IncendiumCompatibility.getProfile("5.5.2").isEmpty());
	}

	@Test
	void fingerprintsResources(@TempDir Path temporaryDirectory) throws IOException {
		Path resource = temporaryDirectory.resolve("resource.mcfunction");
		Files.writeString(resource, "function incendium:test\n", StandardCharsets.UTF_8);

		assertEquals("79e8c6df7eaf2fe744d688608de570354677e9497e61c957dc2639e437fc3752",
				IncendiumCompatibility.sha256(resource));
		assertTrue(IncendiumCompatibility.matches(resource,
				"79e8c6df7eaf2fe744d688608de570354677e9497e61c957dc2639e437fc3752"));
		assertFalse(IncendiumCompatibility.matches(resource,
				"0000000000000000000000000000000000000000000000000000000000000000"));
	}

	@Test
	void packagedFunctionsPreserveSafetyInvariants551() throws IOException {
		String packRoot = "resourcepacks/incendium_5_5_1_optimizations/";
		String main = readPackResource(packRoot, "data/incendium/function/clocks/main.mcfunction");
		String check = readPackResource(packRoot, "data/incendium/function/technical/entity_id/check.mcfunction");
		String reset = readPackResource(packRoot, "data/incendium/function/technical/entity_id/reset.mcfunction");
		String init = readPackResource(packRoot, "data/incendium/function/clocks/lampas_mob_init.mcfunction");

		assertNotNull(init);
		assertTrue(main.contains("function incendium:clocks/lampas_mob_init"));
		assertTrue(main.contains("matches 5.."));
		assertFalse(main.contains("unless score @s in.eid matches 0..32767"));
		assertTrue(main.contains("execute as @e[type=#incendium:other, tag=!in.checked]"));
		assertFalse(main.contains("in.frozen"), "the frozen-mob walk belongs in clocks/lampas_frozen");
		assertTrue(main.contains(
				"execute if score $lampas.frozen in.dummy matches 1.. run function incendium:clocks/lampas_frozen"));
		assertTrue(check.contains("matches 32767.."));
		assertTrue(check.indexOf("entity_id/reset") < check.indexOf("entity_id/init"));
		assertTrue(reset.contains("tag @s add lampas.eid_reset_current"));
		assertTrue(reset.contains("@a[tag=!lampas.eid_reset_current]"));
		assertTrue(reset.contains("type=#incendium:mobs_no_player,tag=in.checked,tag=!lampas.eid_reset_current"));
		assertTrue(reset.contains("tag @s remove lampas.eid_reset_current"));
	}

	@Test
	void packagedHelperCheckForceLoadsHelperChunk551() throws IOException {
		String packRoot = "resourcepacks/incendium_5_5_1_optimizations/";
		String helper = readPackResource(packRoot, "data/incendium/function/technical/summon_helper.mcfunction");

		// The force-load must come before the count, or an unloaded chunk hides existing markers from @e.
		int forceload = helper.indexOf("execute in minecraft:overworld unless loaded 0 0 0 run forceload add 0 0");
		int count = helper.indexOf("execute store result score $count in.dummy if entity @e[type=marker,tag=in.helper_entity]");
		assertTrue(forceload >= 0);
		assertTrue(count > forceload);
		assertTrue(helper.contains("unless score $count in.dummy matches 1 run kill @e[type=marker,tag=in.helper_entity]"));
		assertTrue(helper.contains("in minecraft:overworld run summon marker 0.0 0.0 0.0 {Tags:[\"in.helper_entity\", \"smithed.entity\"]}"));
	}

	@Test
	void playerMainIsUpstreamWithTheHeldItemBlockMovedBehindTheGate551() throws IOException {
		List<String> main = lines("data/incendium/function/player/main.mcfunction");
		List<String> held = lines("data/incendium/function/player/lampas_held_items.mcfunction");

		// The selector form, as upstream writes its predicate checks: it resolves the predicate at run time, so a predicate
		// that fails to load makes the gate false instead of dropping the whole of player/main at function load.
		String gate = "execute if entity @s[predicate=incendium:lampas/holds_incendium_item] run function incendium:player/lampas_held_items";
		int gateLine = main.indexOf(gate);
		assertTrue(gateLine > 0, "player/main must call the gated held-item function");
		assertEquals(gateLine, main.lastIndexOf(gate));
		assertEquals("execute if entity @s[scores={in.trident=1..}] run function incendium:item/voltaic_trident/on_throw",
				lastCommandBefore(main, gateLine), "the gate must stand where upstream's first held-item line ran");

		// The Lampas2 comment above the gate and the header of lampas_held_items are the only added text.
		int commentStart = gateLine;
		while (main.get(commentStart - 1).startsWith("#")) {
			commentStart--;
		}
		assertTrue(main.get(commentStart).startsWith("# Lampas2"));
		int body = held.indexOf("") + 1;
		assertTrue(body > 0 && held.subList(0, body - 1).stream().allMatch(line -> line.startsWith("# ")));

		List<String> rebuilt = new ArrayList<>(main.subList(0, commentStart));
		rebuilt.addAll(held.subList(body, held.size()));
		rebuilt.addAll(main.subList(gateLine + 1, main.size()));
		assertEquals(expectedSha256("data/incendium/function/player/main.mcfunction"),
				sha256(String.join("\n", rebuilt) + "\n"),
				"player/main with lampas_held_items put back must be upstream 5.5.1 byte for byte");

		List<String> moved = held.subList(body, held.size()).stream().filter(line -> line.startsWith("execute")).toList();
		assertEquals(List.of(
				"execute if entity @s[predicate=incendium:holding/voltaic_trident] run function incendium:item/voltaic_trident/holding",
				"execute if entity @s[predicate=incendium:holding/hefty_pickaxe] run function incendium:item/hefty_pickaxe/update",
				"execute if entity @s[predicate=incendium:wearing/scouts_battle_helm] run function incendium:item/scouts_battle_helm/main",
				"execute if entity @s[predicate=incendium:holding/firestorm] run function incendium:item/firestorm/holding",
				"execute if entity @s[predicate=incendium:holding/loaded,predicate=!incendium:holding/loaded_spectral_arrow,predicate=incendium:holding/firestorm] run function incendium:item/firestorm/check_arrow",
				"execute if entity @s[predicate=incendium:holding/loaded,predicate=!incendium:holding/loaded_spectral_arrow,predicate=incendium:holding/holy_wrath] run function incendium:item/patron/holy_wrath/check_arrow",
				"execute if entity @s[predicate=incendium:holding/sentrys_wrath] run function incendium:item/sentrys_wrath/holding"
		), moved);
	}

	@Test
	void heldItemGateTestsIncendiumCustomDataInBothHandsAndHead551() throws IOException {
		JsonObject predicate = JsonParser.parseString(readPackResource(PACK_ROOT,
				"data/incendium/predicate/lampas/holds_incendium_item.json")).getAsJsonObject();
		assertEquals("minecraft:any_of", predicate.get("condition").getAsString());
		JsonArray terms = predicate.getAsJsonArray("terms");
		List<String> slots = new ArrayList<>();
		for (int i = 0; i < terms.size(); i++) {
			JsonObject term = terms.get(i).getAsJsonObject();
			// Exact key sets at every level: any extra condition would narrow the gate below upstream's seven lines.
			assertEquals(java.util.Set.of("condition", "entity", "predicate"), term.keySet());
			assertEquals("minecraft:entity_properties", term.get("condition").getAsString());
			assertEquals("this", term.get("entity").getAsString());
			JsonObject entityPredicate = term.getAsJsonObject("predicate");
			assertEquals(java.util.Set.of("slots"), entityPredicate.keySet());
			JsonObject slotMap = entityPredicate.getAsJsonObject("slots");
			assertEquals(1, slotMap.size());
			String slot = slotMap.keySet().iterator().next();
			slots.add(slot);
			JsonObject itemPredicate = slotMap.getAsJsonObject(slot);
			assertEquals(java.util.Set.of("predicates"), itemPredicate.keySet(), "the slot test must not narrow by item type or count");
			JsonObject componentPredicates = itemPredicate.getAsJsonObject("predicates");
			assertEquals(java.util.Set.of("custom_data"), componentPredicates.keySet());
			assertEquals("{incendium:{}}", componentPredicates.get("custom_data").getAsString());
		}
		assertEquals(List.of("weapon.mainhand", "weapon.offhand", "armor.head"), slots);
		assertEquals(java.util.Set.of("condition", "terms"), predicate.keySet());
	}

	@Test
	void emptyIncendiumCompoundMatchesEveryIncendiumItem() throws Exception {
		// Decoded the way the predicate's custom_data string is, then matched as CustomDataPredicate matches.
		CompoundTag gate = NbtPredicate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("{incendium:{}}"))
				.getOrThrow().tag();
		for (String item : List.of("voltaic_trident", "hefty_pickaxe", "scouts_battle_helm", "firestorm", "holy_wrath",
				"sentrys_wrath")) {
			assertTrue(CustomData.of(TagParser.parseCompoundFully("{incendium:{item:'" + item + "'},other:1b}"))
					.matchedBy(gate), item);
		}
		assertFalse(CustomData.EMPTY.matchedBy(gate));
		assertFalse(CustomData.of(TagParser.parseCompoundFully("{other:{item:'firestorm'}}")).matchedBy(gate));
		assertFalse(CustomData.of(TagParser.parseCompoundFully("{incendium:'firestorm'}")).matchedBy(gate));
	}

	@Test
	void frozenWalkFunctionCountsDownAndReArms551() throws IOException {
		assertEquals(List.of(
				"execute as @e[type=#incendium:mobs,scores={in.frozen=1..},predicate=incendium:random/10] run function incendium:entity/chilling",
				"scoreboard players remove $lampas.frozen in.dummy 1",
				"execute if score $lampas.frozen in.dummy matches ..0 if entity @e[type=#incendium:mobs,scores={in.frozen=1..},limit=1] run scoreboard players set $lampas.frozen in.dummy 200"
		), commands("data/incendium/function/clocks/lampas_frozen.mcfunction"));
	}

	@Test
	void chillingBladeHitIsUpstreamPlusTheArmingLine551() throws IOException {
		List<String> onHit = lines("data/incendium/function/item/chilling_blade/on_hit.mcfunction");
		int arm = onHit.indexOf("scoreboard players set $lampas.frozen in.dummy 200");
		assertEquals(onHit.size() - 1, arm, "the arming line must be the last line");
		assertTrue(onHit.get(arm - 1).startsWith("# Lampas2"));
		assertEquals("", onHit.get(arm - 2));
		assertEquals(expectedSha256("data/incendium/function/item/chilling_blade/on_hit.mcfunction"),
				sha256(String.join("\n", onHit.subList(0, arm - 2)) + "\n"),
				"on_hit without the Lampas2 lines must be upstream 5.5.1 byte for byte");
		assertEquals(1, onHit.stream().filter(line -> !line.startsWith("#") && line.contains("in.frozen")).count());
	}

	private static final String PACK_ROOT = "resourcepacks/incendium_5_5_1_optimizations/";

	private static String expectedSha256(String path) {
		return IncendiumCompatibility.getProfile("5.5.1").orElseThrow().expectedResources().get(path);
	}

	private static String lastCommandBefore(List<String> lines, int index) {
		for (int i = index - 1; i >= 0; i--) {
			if (!lines.get(i).isEmpty() && !lines.get(i).startsWith("#")) {
				return lines.get(i);
			}
		}
		return null;
	}

	/** The file's lines with CRLF folded to LF; the final newline does not produce an empty last line. */
	private static List<String> lines(String path) throws IOException {
		String text = readPackResource(PACK_ROOT, path).replace("\r\n", "\n");
		assertTrue(text.endsWith("\n"), path + " must end with a newline, as upstream's functions do");
		return List.of(text.substring(0, text.length() - 1).split("\n", -1));
	}

	private static List<String> commands(String path) throws IOException {
		return lines(path).stream().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static String readPackResource(String packRoot, String path) throws IOException {
		try (InputStream input = IncendiumCompatibilityTest.class.getClassLoader()
				.getResourceAsStream(packRoot + path)) {
			if (input == null) {
				throw new IOException("Missing test resource: " + packRoot + path);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
