package lampas2overrides.client.defaultpacks;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;

/**
 * Built-in resource packs that Lampas selects by default. Each owning mod registers its pack with
 * {@code PackActivationType.NORMAL}, so a new install lists it under Available. The mixin raises these IDs to
 * {@code DEFAULT_ENABLED}. Fabric API's {@code DefaultResourcePackStorage} then adds a default-enabled pack to the
 * selected list the first time it sees the pack ID and records the ID in {@code data/fabric_default_resource_packs.json};
 * from then on {@code options.txt} decides, so a player who turns a pack off keeps it off. A launch where the ID is
 * not default-enabled (this mod removed, or the ID dropped from this list) removes it from the tracker file, and the
 * next launch with it selects the pack again.
 *
 * <p>A pack ID is the {@code Identifier} the mod passes to {@code registerBuiltinPack}, written as
 * {@code namespace:path}; the pack's files sit under {@code resourcepacks/<path>/} in the mod jar.
 */
public final class DefaultEnabledPacks {

	/** Pack IDs, each with the name the pack screen shows and the mod release it was read from. */
	static final Set<String> PACK_IDS = Set.of(
		// "Default Connected Textures", Continuity 3.0.1+26.2
		"continuity:default",
		// "Glass Pane Culling Fix", Continuity 3.0.1+26.2
		"continuity:glass_pane_culling_fix",
		// "Allay Trails", Elytra Contrails (elytratrails) 1.6.9-26.2
		"elytratrails:allaytrails",
		// "Arrow Trails", Elytra Contrails (elytratrails) 1.6.9-26.2
		"elytratrails:arrowtrails",
		// "More Pillowed (C) Bed Variants", More Bed Variants (quad-lolmbdv) 2.2.0+26.2
		"quad-lolmbdv:more-pillowed-connected-bed-variants");

	static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/default-packs");

	private DefaultEnabledPacks() {
	}

	/** Returns true when the built-in pack with this ID should be selected by default. */
	public static boolean isDefaultEnabled(String packId) {
		return PACK_IDS.contains(packId);
	}

	/**
	 * Logs that a pack registration was raised to default-enabled. It runs on every launch; whether Fabric API then
	 * selects the pack depends on its tracker file and on {@code options.txt}.
	 */
	public static void logRaised(String packId) {
		LOGGER.info("Raised built-in resource pack {} to DEFAULT_ENABLED", packId);
	}
}
