package lampas2overrides.client.frozenwind;

import net.frozenblock.lib.wind.WindManager;

/** Marker and handoff API for a WindManager decoded off the client thread. */
public interface WindManagerDecoded {

	/** Applies this detached decode through FrozenLib's original singleton merge path. */
	WindManager lampas2$applyDecodedToSingleton();
}
