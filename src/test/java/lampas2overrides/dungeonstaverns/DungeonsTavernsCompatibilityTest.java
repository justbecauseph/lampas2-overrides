package lampas2overrides.dungeonstaverns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

public class DungeonsTavernsCompatibilityTest {

	private static final String PACK_ROOT = "resourcepacks/dungeons_and_taverns_5_3_2_optimizations/";

	@Test
	void supportsOnlyVerifiedVersions() {
		assertTrue(DungeonsTavernsCompatibility.supportsVersion("5.3.2"));
		assertFalse(DungeonsTavernsCompatibility.supportsVersion("5.3.1"));
		assertFalse(DungeonsTavernsCompatibility.supportsVersion("5.3.3"));
		assertFalse(DungeonsTavernsCompatibility.supportsVersion("5.3.2+modified"));
		assertFalse(DungeonsTavernsCompatibility.supportsVersion(""));
	}

	@Test
	void versionProfileMapsToPack() {
		Optional<DatapackReplacement.Profile> profile = DungeonsTavernsCompatibility.getProfile("5.3.2");
		assertTrue(profile.isPresent());
		assertEquals(Identifier.fromNamespaceAndPath("lampas2-overrides", "dungeons_and_taverns_5_3_2_optimizations"),
				profile.get().packId());
		assertEquals(4, profile.get().expectedResources().size());
	}

	@Test
	void tickOnlyRemovesTheModifierFromTaggedPlayers() throws IOException {
		String tick = readPackResource("data/nova_structures/function/tick.mcfunction");
		List<String> commands = tick.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();

		assertFalse(tick.contains("execute as @a[predicate=!nova_structures:riding_happy_ghast] run attribute"),
				"the upstream every-player removal must be gone");
		assertTrue(commands.contains(
				"execute as @a[tag=lampas.dnt_fov_boost,predicate=!nova_structures:riding_happy_ghast] run function nova_structures:lampas/clear_fov_boost"));
		assertTrue(commands.contains(
				"execute as @a[tag=!lampas.dnt_fov_migrated] run function nova_structures:lampas/migrate_fov_boost"));
		// Upstream's chart bundle line is kept verbatim.
		assertTrue(commands.contains(
				"execute as @a if items entity @s player.cursor *[minecraft:custom_data~{dnt_chart_bundle:false}] run item modify entity @s player.cursor nova_structures:chart_bundle_ready"));

		String clear = readPackResource("data/nova_structures/function/lampas/clear_fov_boost.mcfunction");
		assertTrue(clear.contains("attribute @s movement_speed modifier remove nova_structures:boost_player_fov"));
		assertTrue(clear.contains("tag @s remove lampas.dnt_fov_boost"));

		String migrate = readPackResource("data/nova_structures/function/lampas/migrate_fov_boost.mcfunction");
		assertTrue(migrate.contains("execute unless predicate nova_structures:riding_happy_ghast run attribute @s movement_speed modifier remove nova_structures:boost_player_fov"));
		assertTrue(migrate.contains("tag @s add lampas.dnt_fov_migrated"));
	}

	@Test
	void swiftSoarFunctionsTagBoostedPassengers() throws IOException {
		String[][] tiers = { { "1", "0.2", "0.1" }, { "2", "0.35", "0.175" }, { "3", "0.5", "0.25" } };
		for (String[] tier : tiers) {
			String function = readPackResource("data/nova_structures/function/swift_soar_" + tier[0] + ".mcfunction");
			List<String> commands = function.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
			assertEquals(8, commands.size(), "upstream has seven commands; the override adds one tag line");
			int tag = commands.indexOf("execute as @s[tag=sprinting] run execute on passengers run tag @s add lampas.dnt_fov_boost");
			int add = commands.indexOf("execute as @s[tag=sprinting] run execute on passengers run attribute @s movement_speed modifier add nova_structures:boost_player_fov "
					+ tier[2] + " add_multiplied_base");
			assertTrue(tag >= 0 && add == tag + 1, "tag must precede the modifier add in tier " + tier[0]);
			assertTrue(commands.contains("execute on controller if predicate nova_structures:forward_key run execute on vehicle as @s[tag=sprinting] run attribute @s flying_speed modifier add nova_structures:sprint_flight_speed "
					+ tier[1] + " add_multiplied_base"));
			assertTrue(commands.contains("execute as @s[tag=!sprinting] run execute on passengers run attribute @s movement_speed modifier remove nova_structures:boost_player_fov"));
		}
	}

	private static String readPackResource(String path) throws IOException {
		try (InputStream input = DungeonsTavernsCompatibilityTest.class.getClassLoader()
				.getResourceAsStream(PACK_ROOT + path)) {
			if (input == null) {
				throw new IOException("Missing test resource: " + PACK_ROOT + path);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
