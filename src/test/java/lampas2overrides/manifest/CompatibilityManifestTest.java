package lampas2overrides.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
		assertTrue(incendium.has("5.5.0"));
		assertTrue(incendium.has("5.5.1"));

		// Formations Overworld 1.0.5+c is metadata-only; its loot tables are upstream-owned.
		JsonObject formations = targets.getAsJsonObject("formationsoverworld").getAsJsonObject("versions");
		assertTrue(formations.has("1.0.5+a"));
		assertTrue(formations.has("1.0.5+c"));
		JsonObject formationsC = formations.getAsJsonObject("1.0.5+c");
		JsonObject formationsCResources = formationsC.getAsJsonObject("resources");
		assertEquals("patched", formationsC.get("status").getAsString());
		assertEquals(1, formationsCResources.size());
		assertEquals("ffa966eb7835cc4de1273945333236331eff33116e918869e4e29c881b39f940",
			formationsCResources.get("pack.mcmeta").getAsString());

		// Verify all registered ResourcePatch entries exist in manifest
		for (Map.Entry<ResourcePatchKey, ResourcePatch> entry : ResourcePatchRegistry.getAllPatches().entrySet()) {
			ResourcePatch patch = entry.getValue();
			String modId = patch.modId();
			assertTrue(targets.has(modId), "Manifest missing target: " + modId);
			JsonObject modObj = targets.getAsJsonObject(modId).getAsJsonObject("versions");
			assertTrue(modObj.has(patch.expectedVersion()),
				"Manifest missing version " + patch.expectedVersion() + " for mod " + modId);
		}
	}
}
