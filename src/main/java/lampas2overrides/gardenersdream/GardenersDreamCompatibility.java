package lampas2overrides.gardenersdream;

import java.util.Map;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

/**
 * Profile for the Gardener's Dream pot-break-check replacement. The replacement rewrites
 * {@code gardenersdream:pot_break_check} to run {@code pot_broken} once per firing instead of once per online
 * player. It is only equivalent while nothing reached from {@code pot_broken} reads the executing player, so the
 * profile fingerprints {@code pot_broken} and both functions it calls as well as the replaced function. The pack is
 * not gated on the mod version: any release whose four functions match the fingerprints audited on 2.6 gets it.
 */
public final class GardenersDreamCompatibility {

	public static final String MOD_ID = "mr_gardeners_dream";
	public static final String MOD_NAME = "Gardener's Dream";
	public static final String AUDITED_VERSION = "2.6";

	private static final DatapackReplacement.Profile PROFILE = new DatapackReplacement.Profile(
			AUDITED_VERSION,
			Identifier.fromNamespaceAndPath("lampas2-overrides", "gardeners_dream_optimizations"),
			"Lampas2 Gardener's Dream optimizations",
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

	private GardenersDreamCompatibility() {
	}

	public static DatapackReplacement.Profile getProfile() {
		return PROFILE;
	}

	/** The single profile keyed by the version its fingerprints were audited on, for the compatibility manifest. */
	public static Map<String, DatapackReplacement.Profile> getProfiles() {
		return Map.of(AUDITED_VERSION, PROFILE);
	}
}
