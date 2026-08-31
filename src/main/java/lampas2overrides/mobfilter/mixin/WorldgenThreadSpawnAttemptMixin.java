package lampas2overrides.mobfilter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.mobfilter.WorldgenDimensionContext;
import net.minecraft.resources.Identifier;

/**
 * Supplies the active worldgen dimension to Mob Filter's worldgen attempt.
 *
 * <p>During world generation, Mob Filter's {@code WorldgenThreadSpawnAttempt.getDimensionId()}
 * normally returns {@code null}, causing {@code DimensionCheck} to falsely match every rule.
 * Returning the scoped dimension allows dimension-restricted rules to evaluate correctly.
 */
@Mixin(targets = "net.pcal.mobfilter.SpawnAttempt$WorldgenThreadSpawnAttempt", remap = false)
public final class WorldgenThreadSpawnAttemptMixin {

	@Inject(method = "getDimensionId", at = @At("HEAD"), cancellable = true, require = 1)
	private void lampas2$provideWorldgenDimension(CallbackInfoReturnable<Identifier> cir) {
		Identifier dimension = WorldgenDimensionContext.get();
		if (dimension != null) {
			cir.setReturnValue(dimension);
		}
	}
}
