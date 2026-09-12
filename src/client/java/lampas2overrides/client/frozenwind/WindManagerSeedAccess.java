package lampas2overrides.client.frozenwind;

import java.util.Optional;

/** Internal access used to preserve FrozenLib's private decoded seed on detached state. */
public interface WindManagerSeedAccess {

	void lampas2$setDecodedSeed(Optional<Long> seed);
}
