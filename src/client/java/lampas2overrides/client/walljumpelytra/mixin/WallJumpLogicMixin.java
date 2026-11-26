package lampas2overrides.client.walljumpelytra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import lampas2overrides.client.walljumpelytra.WallJumpElytraBridge;
import net.minecraft.client.player.LocalPlayer;

/**
 * Blocks wall-clinging while elytra-flying (and while the post-elytra-cancel Shift latch is held) by entering
 * {@code WallJumpLogic.doWallJump}'s reset branch, and cancels elytra flight on wall grab with the cling key.
 */
@Mixin(targets = "com.jahirtrap.walljump.logic.WallJumpLogic", remap = false)
abstract class WallJumpLogicMixin {

	@WrapOperation(method = "doWallJump",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
	private static boolean lampas2$blockWallClingWhileGliding(LocalPlayer player, Operation<Boolean> original) {
		return WallJumpElytraBridge.handleWallClingWhileGliding(player) || original.call(player);
	}
}
