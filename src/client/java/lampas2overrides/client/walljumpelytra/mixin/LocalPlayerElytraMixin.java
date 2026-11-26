package lampas2overrides.client.walljumpelytra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import lampas2overrides.client.walljumpelytra.WallJumpElytraBridge;
import net.minecraft.client.player.LocalPlayer;

/**
 * Prevents vanilla jump-to-elytra activation inside {@code LocalPlayer#aiStep} while the player is clinging to a wall,
 * starting a wall cling, inside the post-wall-jump grace window, or holding an available Wall-Jump TXF double jump.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerElytraMixin {

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void lampas2$trackWallJumpState(CallbackInfo ci) {
		WallJumpElytraBridge.onAiStepHead((LocalPlayer) (Object) this);
	}

	@WrapOperation(method = "aiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
	private boolean lampas2$suppressElytraDuringWallJump(LocalPlayer instance, Operation<Boolean> original) {
		if (WallJumpElytraBridge.shouldSuppressFallFlying(instance)) {
			return false;
		}
		return original.call(instance);
	}
}
