package lampas2overrides.buildersboundry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.datapack.DatapackReplacement;
import net.fabricmc.api.ModInitializer;

/**
 * Replaces Builder's Boundry's per-tick furniture scan (26 untyped {@code @e} selectors per tick) with one
 * selector every four ticks, when the installed release matches the verified profile.
 */
public final class BuildersBoundryOptimization implements ModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/Builder's Boundry");

	@Override
	public void onInitialize() {
		DatapackReplacement.register(
				BuildersBoundryCompatibility.MOD_ID,
				BuildersBoundryCompatibility.MOD_NAME,
				BuildersBoundryCompatibility.getProfiles(),
				LOGGER
		);
	}
}
