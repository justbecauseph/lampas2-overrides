package lampas2overrides.client.walljumpelytra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.player.LocalPlayer;

/**
 * Blocks Wall-Jump TXF double jumps while elytra-flying by steering {@code DoubleJumpLogic.doDoubleJump} into its
 * reset branch when {@code player.isFallFlying()} is true.
 */
@Mixin(targets = "com.jahirtrap.walljump.logic.DoubleJumpLogic", remap = false)
abstract class DoubleJumpLogicMixin {

	@WrapOperation(method = "doDoubleJump",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z"))
	private static boolean lampas2$blockDoubleJumpWhileGliding(LocalPlayer player, Operation<Boolean> original) {
		return original.call(player) || player.isFallFlying();
	}
}
