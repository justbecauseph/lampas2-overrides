package lampas2overrides.client.figurareplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;
import net.minecraft.client.Minecraft;

/**
 * Drives the bridge, one client tick at a time.
 *
 * <p>Deliberately hooks {@code tick} rather than using Fabric's client tick event. A video export
 * drives {@code Minecraft#tick} straight from ReplayMod's render pipeline and never enters the
 * normal game loop, and the bridge's tick count is what its animation clock is built on — so it has
 * to follow the tick that actually happens during an export, not whichever method the lifecycle
 * event happens to be attached to.
 */
@Mixin(Minecraft.class)
public class MinecraftTickMixin {

	@Inject(method = "tick()V", at = @At("RETURN"))
	private void lampas2$tickFiguraReplayBridge(CallbackInfo ci) {
		FiguraReplayBridge.onClientTick();
	}
}
