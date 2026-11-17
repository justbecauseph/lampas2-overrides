package lampas2overrides.gardenersdream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.datapack.DatapackReplacement;
import net.fabricmc.api.ModInitializer;

/**
 * Runs Gardener's Dream's scheduled pot-break check once per firing instead of once per online player (four full
 * entity walks per player), for any release whose functions match the audited fingerprints.
 */
public final class GardenersDreamOptimization implements ModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/Gardener's Dream");

	@Override
	public void onInitialize() {
		DatapackReplacement.registerAnyVersion(
				GardenersDreamCompatibility.MOD_ID,
				GardenersDreamCompatibility.MOD_NAME,
				GardenersDreamCompatibility.getProfile(),
				LOGGER
		);
	}
}
