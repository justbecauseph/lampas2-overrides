package lampas2overrides.dungeonstaverns;

import java.util.Map;
import java.util.Optional;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

/**
 * Version profiles for the Dungeons and Taverns tick-function replacement. The replacement rewrites
 * {@code nova_structures:tick} and the three {@code swift_soar_N} enchantment functions, so the profile
 * fingerprints all four upstream files.
 */
public final class DungeonsTavernsCompatibility {

	public static final String MOD_ID = "mr_dungeons_andtaverns";
	public static final String MOD_NAME = "Dungeons and Taverns";

	private static final DatapackReplacement.Profile PROFILE_5_3_2 = new DatapackReplacement.Profile(
			"5.3.2",
			Identifier.fromNamespaceAndPath("lampas2-overrides", "dungeons_and_taverns_5_3_2_optimizations"),
			"Lampas2 Dungeons and Taverns 5.3.2 optimizations",
			Map.of(
					"data/nova_structures/function/tick.mcfunction",
					"9d831f0686565286a7b13729e95d7af9ab4973955daa98a1561ae9c3451bb48e",
					"data/nova_structures/function/swift_soar_1.mcfunction",
					"8bd5fd81b51cd1d87925bb3878b1a8577b8dcea187616b4000a7f5b46c795a2c",
					"data/nova_structures/function/swift_soar_2.mcfunction",
					"7bb22d468fc0031b98d1cfbc1e6215e75542e558ff985b3bafb3e748b45b5363",
					"data/nova_structures/function/swift_soar_3.mcfunction",
					"486bfd5df1bc2b5dacaf233e9352c25387fd1572cbf49b8496230f2442593c2a"
			)
	);

	private static final Map<String, DatapackReplacement.Profile> PROFILES = Map.of(
			"5.3.2", PROFILE_5_3_2
	);

	private DungeonsTavernsCompatibility() {
	}

	public static boolean supportsVersion(String version) {
		return PROFILES.containsKey(version);
	}

	public static Optional<DatapackReplacement.Profile> getProfile(String version) {
		return Optional.ofNullable(PROFILES.get(version));
	}

	public static Map<String, DatapackReplacement.Profile> getProfiles() {
		return PROFILES;
	}
}
