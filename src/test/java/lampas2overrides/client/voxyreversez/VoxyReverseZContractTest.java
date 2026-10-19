package lampas2overrides.client.voxyreversez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class VoxyReverseZContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.voxyreversez.mixins.json";

	@Test
	void registersTheFeatureAsClientOnlyAndRequired() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("lampas2overrides.client.voxyreversez.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.client.voxyreversez.VoxyReverseZMixinPlugin", config.get("plugin").getAsString());
		assertEquals(Set.of("IrisShaderPatchMixin", "RenderPropertiesMixin", "ViewportAccessor", "VoxyUniformsMixin"),
			stringSet(config.getAsJsonArray("client")));
		assertFalse(config.has("mixins"), "the Voxy mixins must not be common");
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

		JsonObject metadata = readJson("fabric.mod.json");
		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			if (MIXIN_CONFIG.equals(entry.getAsJsonObject().get("config").getAsString())) {
				registered = entry.getAsJsonObject();
			}
		}
		assertNotNull(registered);
		assertEquals("client", registered.get("environment").getAsString());
		assertFalse(metadata.getAsJsonObject("depends").has("voxy"));
		assertFalse(metadata.getAsJsonObject("depends").has("iris"));
	}

	@Test
	void profilesRequireModIdVersionAndHashTogether() {
		var voxy = VoxyReverseZProfiles.VOXY;
		assertTrue(voxy.matches("voxy", "0.2.19-beta", voxy.jarSha256().toUpperCase()));
		assertFalse(voxy.matches("voxy", "0.2.20-beta", voxy.jarSha256()));
		assertFalse(voxy.matches("voxy", "0.2.19-beta", VoxyReverseZProfiles.IRIS.jarSha256()));
		assertFalse(voxy.matches("voxy", "0.2.19-beta", null));
		assertFalse(voxy.matches("iris", "0.2.19-beta", voxy.jarSha256()));

		var iris = VoxyReverseZProfiles.IRIS;
		assertTrue(iris.matches("iris", "1.11.4+mc26.2", iris.jarSha256()));
		// Iris 1.11.2 still switches the game to forward-Z for packs, which Voxy 0.2.19-beta already matches.
		assertFalse(iris.matches("iris", "1.11.2+mc26.2", iris.jarSha256()));
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream input = VoxyReverseZContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, resource);
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static Set<String> stringSet(JsonArray array) {
		Set<String> values = new HashSet<>();
		array.forEach(element -> values.add(element.getAsString()));
		return values;
	}
}
