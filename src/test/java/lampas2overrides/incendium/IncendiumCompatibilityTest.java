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
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.resources.Identifier;

public class IncendiumCompatibilityTest {

	@Test
	void supportsOnlyVerifiedIncendiumVersions() {
		assertTrue(IncendiumCompatibility.supportsVersion("5.5.0"));
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
		Optional<IncendiumCompatibility.Profile> p550 = IncendiumCompatibility.getProfile("5.5.0");
		assertTrue(p550.isPresent());
		assertEquals("5.5.0", p550.get().version());
		assertEquals(Identifier.fromNamespaceAndPath("lampas2-overrides", "incendium_5_5_0_optimizations"), p550.get().packId());
		assertEquals(3, p550.get().expectedResources().size());

		Optional<IncendiumCompatibility.Profile> p551 = IncendiumCompatibility.getProfile("5.5.1");
		assertTrue(p551.isPresent());
		assertEquals("5.5.1", p551.get().version());
		assertEquals(Identifier.fromNamespaceAndPath("lampas2-overrides", "incendium_5_5_1_optimizations"), p551.get().packId());
		assertEquals(3, p551.get().expectedResources().size());

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
	void packagedFunctionsPreserveSafetyInvariants550() throws IOException {
		String packRoot = "resourcepacks/incendium_5_5_0_optimizations/";
		String main = readPackResource(packRoot, "data/incendium/function/clocks/main.mcfunction");
		String check = readPackResource(packRoot, "data/incendium/function/technical/entity_id/check.mcfunction");
		String reset = readPackResource(packRoot, "data/incendium/function/technical/entity_id/reset.mcfunction");
		String init = readPackResource(packRoot, "data/incendium/function/clocks/lampas_mob_init.mcfunction");

		assertNotNull(init);
		assertTrue(main.contains("function incendium:clocks/lampas_mob_init"));
		assertTrue(main.contains("matches 5.."));
		assertFalse(main.contains("unless score @s in.eid matches 0..32767"));
		assertTrue(main.contains("execute as @e[type=#incendium:other, tag=!in.checked]"));
		assertTrue(main.contains("particle minecraft:snowflake"));
		assertTrue(check.contains("matches 32767.."));
		assertTrue(check.indexOf("entity_id/reset") < check.indexOf("entity_id/init"));
		assertTrue(reset.contains("tag @s add lampas.eid_reset_current"));
		assertTrue(reset.contains("@a[tag=!lampas.eid_reset_current]"));
		assertTrue(reset.contains("type=#incendium:mobs_no_player,tag=in.checked,tag=!lampas.eid_reset_current"));
		assertTrue(reset.contains("tag @s remove lampas.eid_reset_current"));
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
		assertTrue(main.contains("run function incendium:entity/chilling"));
		assertTrue(check.contains("matches 32767.."));
		assertTrue(check.indexOf("entity_id/reset") < check.indexOf("entity_id/init"));
		assertTrue(reset.contains("tag @s add lampas.eid_reset_current"));
		assertTrue(reset.contains("@a[tag=!lampas.eid_reset_current]"));
		assertTrue(reset.contains("type=#incendium:mobs_no_player,tag=in.checked,tag=!lampas.eid_reset_current"));
		assertTrue(reset.contains("tag @s remove lampas.eid_reset_current"));
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
