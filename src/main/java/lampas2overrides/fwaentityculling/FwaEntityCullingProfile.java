package lampas2overrides.fwaentityculling;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The one pair of Fancy World Animations and EntityCulling releases the animation culling fix was audited against.
 *
 * <p>Holds no Minecraft types, so the mixin canceller can run on a dedicated server, where the {@code mixinsquared}
 * entrypoint is still instantiated. Vendor classes are named only by string.
 */
public final class FwaEntityCullingProfile {

	public static final String FWA_MOD_ID = "fwa";
	public static final String FWA_VERSION = "1.3.4";
	public static final String ENTITY_CULLING_MOD_ID = "entityculling";
	public static final String ENTITY_CULLING_VERSION = "1.12.0";
	public static final String VENDOR_MIXIN = "fr.madu59.fwa.mixin.client.compat.entityculling.CullTaskMixin";

	private FwaEntityCullingProfile() {
	}

	/** Returns true only when both versions are exactly the audited pair. Null on either side never matches. */
	public static boolean matches(String fwaVersion, String entityCullingVersion) {
		return FWA_VERSION.equals(fwaVersion) && ENTITY_CULLING_VERSION.equals(entityCullingVersion);
	}

	/** Returns the installed mod's version as Fabric Loader reports it, or null when the mod is absent. */
	public static String installedVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse(null);
	}

	/** Returns whether the installed pair is the audited one. Computed once, on first use. */
	public static boolean active() {
		return Holder.ACTIVE;
	}

	/** Defers the Fabric Loader lookup until {@link #active()} is first called. */
	private static final class Holder {
		static final boolean ACTIVE = matches(installedVersion(FWA_MOD_ID), installedVersion(ENTITY_CULLING_MOD_ID));
	}
}
