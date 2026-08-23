package lampas2overrides.incendium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IncendiumCompatibilityTest {

	private static final String PACK_ROOT = "resourcepacks/incendium_5_5_0_optimizations/";

	@Test
	void supportsOnlyVerifiedIncendiumVersion() {
		assertTrue(IncendiumCompatibility.supportsVersion("5.5.0"));
		assertFalse(IncendiumCompatibility.supportsVersion("5.5.1"));
		assertFalse(IncendiumCompatibility.supportsVersion("5.5.0+modified"));
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
	void packagedFunctionsPreserveSafetyInvariants() throws IOException {
		String main = readPackResource("data/incendium/function/clocks/main.mcfunction");
		String check = readPackResource("data/incendium/function/technical/entity_id/check.mcfunction");
		String reset = readPackResource("data/incendium/function/technical/entity_id/reset.mcfunction");

		assertTrue(main.contains("function incendium:clocks/lampas_mob_init"));
		assertTrue(main.contains("matches 5.."));
		assertFalse(main.contains("unless score @s in.eid matches 0..32767"));
		assertTrue(main.contains("execute as @e[type=#incendium:other, tag=!in.checked]"));
		assertTrue(check.contains("matches 32767.."));
		assertTrue(check.indexOf("entity_id/reset") < check.indexOf("entity_id/init"));
		assertTrue(reset.contains("tag @s add lampas.eid_reset_current"));
		assertTrue(reset.contains("@a[tag=!lampas.eid_reset_current]"));
		assertTrue(reset.contains("type=#incendium:mobs_no_player,tag=in.checked,tag=!lampas.eid_reset_current"));
		assertTrue(reset.contains("tag @s remove lampas.eid_reset_current"));
	}

	private static String readPackResource(String path) throws IOException {
		try (InputStream input = IncendiumCompatibilityTest.class.getClassLoader()
				.getResourceAsStream(PACK_ROOT + path)) {
			if (input == null) {
				throw new IOException("Missing test resource: " + path);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
