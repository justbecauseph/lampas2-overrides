package lampas2overrides.lootrfastframes;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import noobanidus.mods.lootr.common.api.helper.SimpleLootrInstance;

/** A block entity needs to inherit the UUID of the Lootr entity it replaces. */
public final class FixedLootrInstance extends SimpleLootrInstance {

	public FixedLootrInstance(Supplier<Set<UUID>> visualOpenersSupplier) {
		super(visualOpenersSupplier, 1);
	}

	public void setId(UUID id) {
		this.id = id;
		this.cachedIdentifier = null;
	}
}
