package lampas2overrides.gardenersdream;

import java.util.Map;
import java.util.Optional;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

/**
 * Version profiles for the Gardener's Dream pot-break-check replacement. The replacement rewrites
 * {@code gardenersdream:pot_break_check} to run {@code pot_broken} once per firing instead of once per online
 * player. It is only equivalent while nothing reached from {@code pot_broken} reads the executing player, so the
 * profile fingerprints {@code pot_broken} and both functions it calls as well as the replaced function.
 */
public final class GardenersDreamCompatibility {

	public static final String MOD_ID = "mr_gardeners_dream";
	public static final String MOD_NAME = "Gardener's Dream";

	private static final DatapackReplacement.Profile PROFILE_2_6 = new DatapackReplacement.Profile(
			"2.6",
			Identifier.fromNamespaceAndPath("lampas2-overrides", "gardeners_dream_2_6_optimizations"),
			"Lampas2 Gardener's Dream 2.6 optimizations",
			Map.of(
					// Replaced.
					"data/gardenersdream/function/pot_break_check.mcfunction",
					"87ba3baeb6b5c8979abb5bf4271ca620757be3760a88cedea5cf08e5afeb7538",
					// Called once per firing instead of once per player; must not read the player.
					"data/gardenersdream/function/pot_broken.mcfunction",
					"151ae3e59a190b97ac47c7b55be9e1149ae30cc294386029f0055a8d2907bede",
					"data/gardenersdream/function/plant_break.mcfunction",
					"d43df0365c966ac7e07f92071aef8c1d0657a09f1c1add4ac53641805bf0bfe6",
					"data/gardenersdream/function/destroy_halfpot.mcfunction",
					"6a597481b65a2b0f33efb1a949f94a68d524c589bc441e5e4bbe6b8b0fcf1e73"
			)
	);

	private static final Map<String, DatapackReplacement.Profile> PROFILES = Map.of(
			"2.6", PROFILE_2_6
	);

	private GardenersDreamCompatibility() {
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
