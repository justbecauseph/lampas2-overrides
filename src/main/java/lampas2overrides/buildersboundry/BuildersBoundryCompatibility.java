package lampas2overrides.buildersboundry;

import java.util.Map;
import java.util.Optional;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

/**
 * Version profiles for the Builder's Boundry tick-function replacement. The replacement rewrites
 * {@code diesse:acs/main} and calls the upstream face, delete and action functions, so the profile fingerprints
 * every upstream function whose content it depends on.
 */
public final class BuildersBoundryCompatibility {

	public static final String MOD_ID = "mr_builders_boundry";
	public static final String MOD_NAME = "Builder's Boundry";

	private static final DatapackReplacement.Profile PROFILE_0_2 = new DatapackReplacement.Profile(
			"0.2",
			Identifier.fromNamespaceAndPath("lampas2-overrides", "builders_boundry_0_2_optimizations"),
			"Lampas2 Builder's Boundry 0.2 optimizations",
			Map.of(
					"data/diesse/function/acs/main.mcfunction",
					"b8bd92884d1ef4014305099756f214525dff80b11eb06de0614320ffda6a65df",
					"data/diesse/function/acs/face/all.mcfunction",
					"b261598a809be7a22fc198bcdab90a6e4b0f3e234329363777ec21851997b94c",
					"data/diesse/function/acs/actions/all.mcfunction",
					"a18fe12b6edf5b3417eda09d76791e9609c29bea158add12191629095fce1998",
					"data/diesse/function/acs/face/top.mcfunction",
					"cf9bbc4a20d203e5cfacb9738e1a33dba7e7f23c140cd14047ddbe0889da0f54",
					"data/diesse/function/acs/face/side.mcfunction",
					"f7322194b3a159259f6b6f74dd7f169d24f0f66d825bcfefdb08a1e6623cf59a",
					"data/diesse/function/acs/delete/all.mcfunction",
					"6278b8d0aa8206fdd995b92f385b11e45dbc1b839a9ba50c5df7517b80eda151",
					"data/diesse/function/acs/reset.mcfunction",
					"7429ff359c9f734b2e92713fe138c807422e23accef67da7d2b6de46e8f4293b"
			)
	);

	private static final Map<String, DatapackReplacement.Profile> PROFILES = Map.of(
			"0.2", PROFILE_0_2
	);

	private BuildersBoundryCompatibility() {
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
