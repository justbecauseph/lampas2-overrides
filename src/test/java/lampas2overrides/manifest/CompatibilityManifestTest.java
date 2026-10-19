package lampas2overrides.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lampas2overrides.buildersboundry.BuildersBoundryCompatibility;
import lampas2overrides.datapack.DatapackReplacement;
import lampas2overrides.dungeonstaverns.DungeonsTavernsCompatibility;
import lampas2overrides.incendium.IncendiumCompatibility;
import lampas2overrides.resourcefix.ResourcePatch;
import lampas2overrides.resourcefix.ResourcePatchKey;
import lampas2overrides.resourcefix.ResourcePatchRegistry;

public class CompatibilityManifestTest {

	@Test
	void manifestMatchesCodebaseState() throws Exception {
		JsonObject manifest;
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("compatibility-targets.json")) {
			assertNotNull(is, "Missing compatibility-targets.json on classpath");
			manifest = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
		}

		assertEquals(1, manifest.get("schema").getAsInt());
		assertEquals("26.2", manifest.get("minecraft").getAsString());
		JsonObject targets = manifest.getAsJsonObject("targets");
		assertNotNull(targets);

		// Verify Incendium profiles match manifest
		assertTrue(targets.has("incendium"));
		JsonObject incendium = targets.getAsJsonObject("incendium").getAsJsonObject("versions");
		assertTrue(incendium.has("5.5.1"));
		assertTrue(IncendiumCompatibility.supportsVersion("5.5.1"));

		// Datapack replacements built on DatapackReplacement list exactly their fingerprinted upstream resources.
		assertDatapackReplacement(targets, BuildersBoundryCompatibility.MOD_ID, BuildersBoundryCompatibility.getProfiles());
		assertDatapackReplacement(targets, DungeonsTavernsCompatibility.MOD_ID, DungeonsTavernsCompatibility.getProfiles());

		// Formations Overworld 1.0.5+c is metadata-only; its loot tables are upstream-owned.
		JsonObject formations = targets.getAsJsonObject("formationsoverworld").getAsJsonObject("versions");
		assertTrue(formations.has("1.0.5+c"));
		JsonObject formationsC = formations.getAsJsonObject("1.0.5+c");
		JsonObject formationsCResources = formationsC.getAsJsonObject("resources");
		assertEquals("patched", formationsC.get("status").getAsString());
		assertEquals(1, formationsCResources.size());
		assertEquals("ffa966eb7835cc4de1273945333236331eff33116e918869e4e29c881b39f940",
			formationsCResources.get("pack.mcmeta").getAsString());

		JsonObject wilderWild433 = targets.getAsJsonObject("wilderwild").getAsJsonObject("versions").getAsJsonObject("4.3.3");
		assertEquals("patched", wilderWild433.get("status").getAsString());
		assertEquals("59cf59a1c1e86f9627361e1dcec3250b81d535c6e84ff7c3b2950d9b90f2efe4",
			wilderWild433.getAsJsonObject("resources")
				.get("data/wilderwild/worldgen/configured_feature/stone_pool.json").getAsString());
		assertEquals("dfcc128df21715b20fe86bba18c5b9f731a66d3854269044ead125f6f9c20204",
			wilderWild433.get("artifact_sha256").getAsString());

		JsonObject rogues = targets.getAsJsonObject("rogues").getAsJsonObject("versions").getAsJsonObject("3.1.3.003+26.2");
		assertEquals("patched", rogues.get("status").getAsString());
		assertEquals("fe176357898633059d7e37643e19115d25352725a0015630f92f39b2f96f620e",
			rogues.getAsJsonObject("resources").get("data/minecraft/enchantment/sharpness.json").getAsString());
		assertEquals("77bdff881c01011bff6e6e722a1f47dc01ca7d377a1f08231d305fc11b08f113",
			rogues.get("artifact_sha256").getAsString());
		assertEquals(12, rogues.getAsJsonObject("resources").size(), "Sharpness plus eleven spells");

		// RPG Series spell patches (D3 P1, D9): jar SHA-256 and resource count per target.
		String[][] spellTargets = {
			{"spell_engine", "1.10.9+26.2", "bfc258875662071d76c448e603f05f0f519bf3e8a0c32207d232d820cab0ab2a", "10"},
			{"paladins", "3.1.3.003+26.2", "f36009e6a60fae66ca195fc9165a2d7a59c92bd55fd75b1e2e18620ffa4fc492", "1"},
			{"archers", "3.1.3.003+26.2", "4ee9c35f54d74f28f742f36a4f0b319d4d3936bc0731bd95cb823c17225db22d", "5"},
			{"wizards", "3.1.3.003+26.2", "ced172215b3f5fb4301252d171e26c621fbebcc579fc67bd639fb2d92a8034a2", "5"},
			{"skill_tree_rpgs", "1.6.2+26.2", "405ff761ba518c87a14d8923f0c8db5f01311b86d58ca78c3e1f2731d3469901", "18"},
			{"relics_rpgs", "1.4.1+26.2", "013eae223a352138342e5d629e5fec75ee7f19405b82589435993f403be8469f", "14"}
		};
		for (String[] target : spellTargets) {
			JsonObject version = targets.getAsJsonObject(target[0]).getAsJsonObject("versions").getAsJsonObject(target[1]);
			assertNotNull(version, target[0] + " " + target[1]);
			assertEquals("patched", version.get("status").getAsString(), target[0]);
			assertEquals(target[2], version.get("artifact_sha256").getAsString(), target[0]);
			assertEquals(Integer.parseInt(target[3]), version.getAsJsonObject("resources").size(), target[0]);
			assertEquals("docs/evidence/rpg-spell-patches.json", version.get("probe_evidence").getAsString(), target[0]);
			assertTrue(version.has("verification_limit"), target[0]);
		}

		JsonObject puzzles = targets.getAsJsonObject("puzzleslib").getAsJsonObject("versions");
		assertTrue(puzzles.has("26.2.5"));

		JsonObject chatting = targets.getAsJsonObject("chatting").getAsJsonObject("versions");
		assertTrue(chatting.has("3.2.3"));

		// Verify all registered ResourcePatch entries exist in manifest
		Map<String, Map<String, String>> registered = new HashMap<>();
		for (Map.Entry<ResourcePatchKey, ResourcePatch> entry : ResourcePatchRegistry.getAllPatches().entrySet()) {
			ResourcePatch patch = entry.getValue();
			String modId = patch.modId();
			assertTrue(targets.has(modId), "Manifest missing target: " + modId);
			JsonObject modObj = targets.getAsJsonObject(modId).getAsJsonObject("versions");
			assertTrue(modObj.has(patch.expectedVersion()),
				"Manifest missing version " + patch.expectedVersion() + " for mod " + modId);
			registered.computeIfAbsent(modId + " " + patch.expectedVersion(), key -> new HashMap<>())
				.put(patch.resourcePath(), patch.expectedSha256());
		}

		// Each patched version's resources map lists exactly the registered paths and upstream SHA-256 values.
		for (Map.Entry<String, Map<String, String>> entry : registered.entrySet()) {
			String[] modAndVersion = entry.getKey().split(" ");
			JsonObject resources = targets.getAsJsonObject(modAndVersion[0]).getAsJsonObject("versions")
				.getAsJsonObject(modAndVersion[1]).getAsJsonObject("resources");
			assertNotNull(resources, "Manifest missing resources for " + entry.getKey());
			Map<String, String> listed = new HashMap<>();
			resources.entrySet().forEach(resource -> listed.put(resource.getKey(), resource.getValue().getAsString()));
			assertEquals(entry.getValue(), listed, "Manifest resources differ from the registry for " + entry.getKey());
		}
	}

	private static void assertDatapackReplacement(JsonObject targets, String modId,
			Map<String, DatapackReplacement.Profile> profiles) {
		assertTrue(targets.has(modId), "Manifest missing target: " + modId);
		JsonObject target = targets.getAsJsonObject(modId);
		assertEquals("datapack_replacement", target.get("type").getAsString());
		JsonObject versions = target.getAsJsonObject("versions");
		assertEquals(profiles.size(), versions.size(), "Manifest version count differs for " + modId);
		for (Map.Entry<String, DatapackReplacement.Profile> entry : profiles.entrySet()) {
			JsonObject version = versions.getAsJsonObject(entry.getKey());
			assertNotNull(version, "Manifest missing version " + entry.getKey() + " for mod " + modId);
			assertEquals(entry.getValue().packId().toString(), version.get("pack_id").getAsString());
			Map<String, String> listed = new HashMap<>();
			version.getAsJsonObject("resources").entrySet()
				.forEach(resource -> listed.put(resource.getKey(), resource.getValue().getAsString()));
			assertEquals(entry.getValue().expectedResources(), listed,
				"Manifest resources differ from the profile for " + modId + " " + entry.getKey());
		}
	}
}
