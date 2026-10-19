package lampas2overrides.buildersboundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

public class BuildersBoundryCompatibilityTest {

	private static final String PACK_ROOT = "resourcepacks/builders_boundry_0_2_optimizations/";

	@Test
	void supportsOnlyVerifiedVersions() {
		assertTrue(BuildersBoundryCompatibility.supportsVersion("0.2"));
		assertFalse(BuildersBoundryCompatibility.supportsVersion("0.1"));
		assertFalse(BuildersBoundryCompatibility.supportsVersion("0.3"));
		assertFalse(BuildersBoundryCompatibility.supportsVersion("0.2+modified"));
		assertFalse(BuildersBoundryCompatibility.supportsVersion(""));
	}

	@Test
	void versionProfileMapsToPack() {
		Optional<DatapackReplacement.Profile> profile = BuildersBoundryCompatibility.getProfile("0.2");
		assertTrue(profile.isPresent());
		assertEquals("0.2", profile.get().version());
		assertEquals(Identifier.fromNamespaceAndPath("lampas2-overrides", "builders_boundry_0_2_optimizations"),
				profile.get().packId());
		// main (replaced) plus every upstream function the replacement calls or re-implements.
		assertEquals(7, profile.get().expectedResources().size());
		assertTrue(profile.get().expectedResources().containsKey("data/diesse/function/acs/main.mcfunction"));
		assertTrue(profile.get().expectedResources().containsKey("data/diesse/function/acs/actions/all.mcfunction"));
	}

	@Test
	void mainRunsOneEntityScanEveryFourTicks() throws IOException {
		String main = readPackResource("data/diesse/function/acs/main.mcfunction");
		String slow = readPackResource("data/diesse/function/acs/lampas/slow.mcfunction");
		String entity = readPackResource("data/diesse/function/acs/lampas/entity.mcfunction");

		assertEquals(0, countSelectors(main, "@e"), "main must not scan entities every tick");
		assertTrue(main.contains("matches 4.. run function diesse:acs/lampas/slow"));
		assertEquals(1, countSelectors(slow, "@e"), "slow runs exactly one entity scan");
		assertTrue(slow.contains("execute as @e[tag=acs.acc.block] at @s run function diesse:acs/lampas/entity"));
		assertEquals(0, countSelectors(entity, "@e"), "per-entity dispatch must only use @s");
		// The acs.place reset must follow the scan so face/top and face/side still find the placing player.
		assertTrue(slow.indexOf("scoreboard players set @a[scores={acs.place=1..}] acs.place 0")
				> slow.indexOf("diesse:acs/lampas/entity"));
	}

	@Test
	void entityDispatchPreservesUpstreamOrderAndStopsAfterRemoval() throws IOException {
		String entity = readPackResource("data/diesse/function/acs/lampas/entity.mcfunction");
		List<String> commands = entity.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();

		// Upstream main: support checks, then face/all, then actions/all.
		int deleteTop = indexOfLine(commands, "tag=acs.face.top] if block ~ ~-1 ~ air run return run function diesse:acs/delete/all");
		int deleteSide = indexOfLine(commands, "tag=acs.face.side] if block ^ ^ ^-0.5 air run return run function diesse:acs/delete/all");
		int faceTop = indexOfLine(commands, "run return run function diesse:acs/face/top");
		int faceSide = indexOfLine(commands, "run return run function diesse:acs/face/side");
		int firstAction = indexOfLine(commands, "diesse:acs/actions/tall_lamp");
		assertTrue(deleteTop >= 0 && deleteSide > deleteTop && faceTop > deleteSide && faceSide > faceTop
				&& firstAction > faceSide);

		// Every upstream actions/all target with its tag, so a furniture entity gets the same action as before.
		for (String lamp : List.of("black", "light_gray", "gray", "blue", "light_blue", "yellow", "pink", "purple",
				"white", "brown", "lime", "green", "magenta", "cyan", "orange", "red")) {
			assertTrue(commands.contains("execute if entity @s[tag=acs." + lamp + "_lamp] run function diesse:acs/actions/lamp"),
					lamp);
		}
		assertTrue(commands.contains("execute if entity @s[tag=acs.candlestick] run function diesse:acs/actions/candlestick"));
		assertTrue(commands.contains("execute if entity @s[tag=acs.gold_candlestick] run function diesse:acs/actions/candlestick"));
		assertTrue(commands.contains("execute if entity @s[tag=acs.clock] run function diesse:acs/actions/clock/all"));
		assertTrue(commands.contains(
				"execute if entity @s[tag=acs.cutting_board,nbt={ItemRotation:1b}] run function diesse:acs/actions/cutting_board/used"));
	}

	private static int indexOfLine(List<String> commands, String fragment) {
		for (int i = 0; i < commands.size(); i++) {
			if (commands.get(i).contains(fragment)) {
				return i;
			}
		}
		return -1;
	}

	private static int countSelectors(String function, String selector) {
		Matcher matcher = Pattern.compile(Pattern.quote(selector)).matcher(
				function.lines().filter(line -> !line.startsWith("#")).reduce("", (a, b) -> a + "\n" + b));
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	private static String readPackResource(String path) throws IOException {
		try (InputStream input = BuildersBoundryCompatibilityTest.class.getClassLoader()
				.getResourceAsStream(PACK_ROOT + path)) {
			if (input == null) {
				throw new IOException("Missing test resource: " + PACK_ROOT + path);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
