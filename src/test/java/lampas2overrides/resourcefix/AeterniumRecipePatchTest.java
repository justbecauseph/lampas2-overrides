package lampas2overrides.resourcefix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Paladins and Rogues 3.1.3.003+26.2 aeternium recipes. Upstream writes the handle key as
 * {@code ["#c:wood_sticks", "minecraft:stick"]}; the 26.2 ingredient codec reads a list as item ids only, so the
 * server logged "Not a valid resource location: #c:wood_sticks" and dropped all seven recipes.
 */
public class AeterniumRecipePatchTest {

	private static final String VERSION = "3.1.3.003+26.2";
	private static final Map<String, String> RECIPES = Map.of(
		"paladins/claymore", "fa5e637b0bc9a4c9a44d38e42cd1fa846d9430e76e835a3c8c283975b431c82c",
		"paladins/great_hammer", "820b2782dbf37a61ddf3722eb1a40354fad5ef75a015cc08b50bb0dbcff33622",
		"paladins/mace", "67265d2248c2c53c82217fed21f3f75b3bf449a936fa5f1ad423629cf8dd07fa",
		"rogues/dagger", "50d78f25dda5b29943529d6ca91225e4fceb403cdb387cce6d3e85353420e1e8",
		"rogues/double_axe", "25abbc0b23f2aa5485351370763990f008d4af0bb424d0b30ed8ea675824a648",
		"rogues/glaive", "b2da2ec235865b2f1e9613f764199904db61d00718bc5ef1c4a7e02ecef89fdf",
		"rogues/sickle", "2277d4e7cb73b661c964dad281e116c86c9dbeb08e7abe0abc6784fa38613483"
	);

	@BeforeEach
	void setUp() {
		ResourcePatchResolver.clearLogHistoryForTests();
	}

	@Test
	void replacementChangesOnlyTheTaggedStickKey() throws IOException {
		for (Map.Entry<String, String> recipe : RECIPES.entrySet()) {
			String modId = modId(recipe.getKey());
			String path = path(recipe.getKey());
			byte[] upstream = readRaw("upstream/" + modId + "/" + VERSION + "/" + path);
			assertEquals(recipe.getValue(), ResourcePatchResolver.sha256Hex(upstream), "Fixture is not the deployed jar entry: " + path);

			ResourcePatch patch = ResourcePatchRegistry.findPatch(modId, VERSION, path);
			assertNotNull(patch, path);
			assertEquals(recipe.getValue(), patch.expectedSha256(), path);

			JsonObject expected = parse(upstream);
			JsonObject key = expected.getAsJsonObject("key");
			int replaced = 0;
			for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
				if (entry.getValue().isJsonArray()) {
					assertEquals("[\"#c:wood_sticks\",\"minecraft:stick\"]", entry.getValue().toString(), path);
					entry.setValue(JsonParser.parseString("\"minecraft:stick\""));
					replaced++;
				}
			}
			assertEquals(1, replaced, "Exactly one list key per upstream recipe: " + path);

			JsonObject replacement = parse(readRaw(patch.replacementPath()));
			assertEquals(expected, replacement, "Replacement must equal upstream with only the stick key changed: " + path);
			// The 26.2 ingredient codec reads a key as one item id, one "#tag", or a list of item ids.
			for (Map.Entry<String, JsonElement> entry : replacement.getAsJsonObject("key").entrySet()) {
				assertTrue(entry.getValue().isJsonPrimitive(), path + " key " + entry.getKey());
				assertFalse(entry.getValue().getAsString().startsWith("#"), path + " key " + entry.getKey());
			}
			// The BetterEnd load condition stays, so the recipes only exist with aeternium present.
			assertEquals("betterend", replacement.getAsJsonArray("fabric:load_conditions").get(0).getAsJsonObject()
				.getAsJsonArray("values").get(0).getAsString(), path);
		}
	}

	@Test
	void appliesForDeclaredVersionAndHash() throws IOException {
		for (String recipe : RECIPES.keySet()) {
			String modId = modId(recipe);
			String path = path(recipe);
			byte[] upstream = readRaw("upstream/" + modId + "/" + VERSION + "/" + path);
			try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
				IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
					ResourcePatchResolverTest.createMockModContainer(modId, VERSION), path, () -> new ByteArrayInputStream(upstream));
				assertNotNull(patched, path);
				try (InputStream in = patched.get()) {
					assertEquals("minecraft:stick", parse(in.readAllBytes()).getAsJsonObject("key")
						.get(modId.equals("paladins") ? "H" : "S").getAsString(), path);
				}
				assertTrue(logs.has(Level.INFO, "Applied " + modId + " " + VERSION + " compatibility resource: " + path),
					"Expected the applied line: " + logs.messages());
			}
		}
	}

	@Test
	void keepsUpstreamForOtherVersionOrChangedFile() throws IOException {
		String path = path("rogues/dagger");
		byte[] upstream = readRaw("upstream/rogues/" + VERSION + "/" + path);
		try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
			assertNull(ResourcePatchResolver.resolve(ResourcePatchResolverTest.createMockModContainer("rogues", "3.1.4.1+26.3"),
				path, () -> new ByteArrayInputStream(upstream)));
			assertTrue(logs.has(Level.WARN, "Skipping rogues resource patch for " + path), logs.messages().toString());
		}

		byte[] changed = new String(upstream, StandardCharsets.UTF_8).replace("equipment", "misc").getBytes(StandardCharsets.UTF_8);
		try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
			IoSupplier<InputStream> resolved = ResourcePatchResolver.resolve(
				ResourcePatchResolverTest.createMockModContainer("rogues", VERSION), path, () -> new ByteArrayInputStream(changed));
			assertNotNull(resolved);
			try (InputStream in = resolved.get()) {
				assertArrayEquals(changed, in.readAllBytes(), "A changed upstream file must pass through unchanged");
			}
			assertTrue(logs.has(Level.WARN, "SHA-256 mismatch (expected " + RECIPES.get("rogues/dagger")), logs.messages().toString());
		}
	}

	private static String modId(String recipe) {
		return recipe.substring(0, recipe.indexOf('/'));
	}

	private static String path(String recipe) {
		return "data/" + modId(recipe) + "/recipe/aeternium_" + recipe.substring(recipe.indexOf('/') + 1) + ".json";
	}

	/** Raw classpath bytes: these jar entries end in LF, which {@code readFixtureResource} would strip. */
	private static byte[] readRaw(String resource) throws IOException {
		try (InputStream input = AeterniumRecipePatchTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, "Missing test resource: " + resource);
			return input.readAllBytes();
		}
	}

	private static JsonObject parse(byte[] bytes) {
		return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
