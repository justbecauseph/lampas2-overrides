package lampas2overrides.dungeonstaverns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.datapack.DatapackReplacement;
import net.fabricmc.api.ModInitializer;

/**
 * Limits Dungeons and Taverns' per-tick Swift Soar FOV-modifier removal to players that actually carry the
 * modifier, when the installed release matches the verified profile.
 */
public final class DungeonsTavernsOptimization implements ModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/Dungeons and Taverns");

	@Override
	public void onInitialize() {
		DatapackReplacement.register(
				DungeonsTavernsCompatibility.MOD_ID,
				DungeonsTavernsCompatibility.MOD_NAME,
				DungeonsTavernsCompatibility.getProfiles(),
				LOGGER
		);
	}
}
