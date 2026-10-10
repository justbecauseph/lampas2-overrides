package lampas2overrides.illagerblabber;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Installs the IllagerBlabber registry cleanup only after the exact artifact gate passes.
 *
 * <p>This class names no IllagerBlabber type or accessor mixin. Vendor state is reached only through
 * {@link IllagerBlabberRegistryCleanup}, and only after the gate has returned true; the mixin plugin
 * applies the accessors under the same gate.
 */
public final class IllagerBlabberFixes implements ModInitializer {

	@Override
	public void onInitialize() {
		if (!IllagerBlabberProfile.isInstalledSupported(FabricLoader.getInstance())) {
			return;
		}
		// Load the registry and its accessors now, so a moved field stops startup rather than a tick.
		IllagerBlabberRegistryCleanup.perEntityMaps();
		ServerEntityEvents.ENTITY_UNLOAD.register(
			(entity, level) -> IllagerBlabberRegistryCleanup.onEntityUnload(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(
			server -> IllagerBlabberRegistryCleanup.clearAllState());
	}
}
