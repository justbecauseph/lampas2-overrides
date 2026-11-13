package lampas2overrides.fwaentityculling;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.bawnorton.mixinsquared.api.MixinCanceller;

/**
 * Cancels Fancy World Animations' {@code CullTaskMixin} for the audited pair only.
 *
 * <p>That mixin shadows {@code CullTask.culling} as {@code com.logisticscraft.occlusionculling.OcclusionCullingInstance}.
 * EntityCulling 1.12.0 retyped the field to its own {@code OcclusionCulling} interface, so the shadow fails at startup.
 * {@code CullTaskAnimationCullingMixin} in the client source set re-adds the same culling against the new type. Any other mixin, and this one under any other version pair, is left alone.
 */
public final class FwaCullTaskMixinCanceller implements MixinCanceller {

	@Override
	public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
		return shouldCancel(mixinClassName, FwaEntityCullingProfile::active);
	}

	/** Returns true only for the vendor mixin name. The name is compared first, so other mixins never reach {@code active}. */
	static boolean shouldCancel(String mixinClassName, BooleanSupplier active) {
		return FwaEntityCullingProfile.VENDOR_MIXIN.equals(mixinClassName) && active.getAsBoolean();
	}
}
