package lampas2overrides.client.figurareplay.mixin.replaymod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;

/**
 * Drives Figura's animations once per exported video frame.
 *
 * <p>Figura applies animation transforms at the head of {@code Minecraft#runTick} and clears them
 * at its return. Video export never goes through {@code runTick} — it drives {@code Minecraft#tick}
 * and the world renderer directly from its own pipeline — so without this, exported footage shows
 * every avatar frozen in whatever pose it held when rendering began.
 *
 * <p>{@code updateForNextFrame} is the pipeline's one frame boundary, called once per output frame
 * however many views that frame is stitched from, so cubic and stereoscopic exports advance
 * animations once per frame rather than once per view.
 */
@Mixin(targets = "com.replaymod.render.rendering.VideoRenderer", remap = false)
public class VideoRendererMixin {

	@Inject(method = "updateForNextFrame()F", at = @At("HEAD"))
	private void lampas2$clearAnimationsFromLastFrame(CallbackInfoReturnable<Float> cir) {
		FiguraReplayBridge.beforeExportedFrame();
	}

	@Inject(method = "updateForNextFrame()F", at = @At("RETURN"))
	private void lampas2$applyAnimationsForThisFrame(CallbackInfoReturnable<Float> cir) {
		FiguraReplayBridge.afterExportedFrame();
	}
}
