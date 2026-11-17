package lampas2overrides.gardenersdream;

import java.util.Map;

import lampas2overrides.datapack.DatapackReplacement;
import net.minecraft.resources.Identifier;

/**
 * Profile for the Gardener's Dream pot-break-check replacement. The replacement rewrites
 * {@code gardenersdream:pot_break_check} to run {@code pot_broken} once per firing instead of once per online
 * player. It is only equivalent while nothing reached from {@code pot_broken} reads the executing player; that was
 * checked on 2.6. The pack is gated on neither the mod version nor the content of the upstream functions, so a later
 * release that changes {@code pot_broken}, {@code plant_break} or {@code destroy_halfpot} still gets the override and
 * has to be re-audited by hand.
 */
public final class GardenersDreamCompatibility {

	public static final String MOD_ID = "mr_gardeners_dream";
	public static final String MOD_NAME = "Gardener's Dream";
	public static final String AUDITED_VERSION = "2.6";

	private static final DatapackReplacement.Profile PROFILE = new DatapackReplacement.Profile(
			AUDITED_VERSION,
			Identifier.fromNamespaceAndPath("lampas2-overrides", "gardeners_dream_optimizations"),
			"Lampas2 Gardener's Dream optimizations",
			// No fingerprints: the pack registers whenever the mod is installed. The SHA-256 of the four functions the
			// equivalence was checked against (2.6) are recorded under audited_resources in compatibility-targets.json.
			Map.of()
	);

	private GardenersDreamCompatibility() {
	}

	public static DatapackReplacement.Profile getProfile() {
		return PROFILE;
	}

	/** The single profile keyed by the version the override was audited on, for the compatibility manifest. */
	public static Map<String, DatapackReplacement.Profile> getProfiles() {
		return Map.of(AUDITED_VERSION, PROFILE);
	}
}
