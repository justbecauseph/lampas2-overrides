package lampas2overrides.gardenersdream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

public class GardenersDreamCompatibilityTest {

	private static final String PACK_ROOT = "resourcepacks/gardeners_dream_optimizations/";

	@Test
	void profileMapsToPackAndFingerprintsTheWholeChain() {
		DatapackReplacement.Profile profile = GardenersDreamCompatibility.getProfile();
		// The version only records where the fingerprints were audited; registration does not compare it.
		assertEquals("2.6", profile.version());
		assertEquals(Identifier.fromNamespaceAndPath("lampas2-overrides", "gardeners_dream_optimizations"), profile.packId());
		// The replaced function plus pot_broken and the two functions it calls, which must not read the player.
		assertEquals(4, profile.expectedResources().size());
		for (String function : List.of("pot_break_check", "pot_broken", "plant_break", "destroy_halfpot")) {
			assertTrue(profile.expectedResources().containsKey(
					"data/gardenersdream/function/" + function + ".mcfunction"), function);
		}
		assertEquals(java.util.Map.of("2.6", profile), GardenersDreamCompatibility.getProfiles());
	}

	@Test
	void potBreakCheckRunsPotBrokenOncePerFiring() throws IOException {
		List<String> commands = commands("data/gardenersdream/function/pot_break_check.mcfunction");

		assertEquals(4, commands.size());
		// One pass, and only while a player is online, as upstream's `execute as @a` ran nothing with nobody online.
		assertEquals("execute if entity @a[limit=1] run function gardenersdream:pot_broken", commands.get(0));
		assertTrue(commands.stream().noneMatch(command -> command.contains("execute as @a")),
				"pot_broken must not run once per player");
		assertEquals(1, commands.stream().filter(command -> command.contains("gardenersdream:pot_broken")).count());
	}

	@Test
	void potBreakCheckKeepsUpstreamReschedule() throws IOException {
		List<String> commands = commands("data/gardenersdream/function/pot_break_check.mcfunction");

		// Upstream 2.6's three reschedule lines, verbatim: the _settings speeds 3/2/1 map to 2.0s/1.0s/0.3s.
		assertEquals(List.of(
				"execute if score #gd_setting gd_check_delay matches 3 run schedule function gardenersdream:pot_break_check 2.0s",
				"execute if score #gd_setting gd_check_delay matches 2 run schedule function gardenersdream:pot_break_check 1.0s",
				"execute if score #gd_setting gd_check_delay matches 1 run schedule function gardenersdream:pot_break_check 0.3s"
		), commands.subList(1, 4));
	}

	@Test
	void packReplacesOnlyPotBreakCheck() throws IOException {
		try (InputStream mcmeta = resource("pack.mcmeta")) {
			assertTrue(mcmeta != null, "pack.mcmeta missing");
			String text = new String(mcmeta.readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(text.contains("\"min_format\": [107, 1]") && text.contains("\"max_format\": [107, 1]"), text);
		}
		for (String other : List.of("pot_broken", "plant_break", "destroy_halfpot")) {
			try (InputStream input = resource("data/gardenersdream/function/" + other + ".mcfunction")) {
				assertTrue(input == null, other + " must stay upstream's");
			}
		}
	}

	private static List<String> commands(String path) throws IOException {
		try (InputStream input = resource(path)) {
			if (input == null) {
				throw new IOException("Missing test resource: " + PACK_ROOT + path);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
					.map(String::strip)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.toList();
		}
	}

	private static InputStream resource(String path) {
		return GardenersDreamCompatibilityTest.class.getClassLoader().getResourceAsStream(PACK_ROOT + path);
	}
}
