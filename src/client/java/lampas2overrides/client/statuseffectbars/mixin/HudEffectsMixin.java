package lampas2overrides.client.statuseffectbars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import lampas2overrides.client.statuseffectbars.EffectIconPosition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;

/**
 * Opens a capture window around each effect background blit in {@code Hud#extractEffects}, and clears the record
 * when the method starts and ends.
 *
 * <p>Both background call sites use the six-argument {@code blitSprite(RenderPipeline, Identifier, int, int, int, int)}
 * overload, 24 by 24 at the effect's position. The wrap only opens and closes the window; the blit itself is the one
 * {@link GuiGraphicsExtractorMixin} records. The icon blit uses the five-integer overload and falls outside the window.
 */
@Mixin(Hud.class)
public class HudEffectsMixin {

	private static final String EXTRACT_EFFECTS =
		"extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";
	private static final String BACKGROUND_BLIT =
		"Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
			+ "Lnet/minecraft/resources/Identifier;IIII)V";

	@Inject(method = EXTRACT_EFFECTS, at = @At("HEAD"))
	private void lampas2$resetEffectPosition(CallbackInfo ci) {
		EffectIconPosition.reset();
	}

	@Inject(method = EXTRACT_EFFECTS, at = @At("RETURN"))
	private void lampas2$clearEffectPosition(CallbackInfo ci) {
		EffectIconPosition.reset();
	}

	@WrapOperation(method = EXTRACT_EFFECTS, at = @At(value = "INVOKE", target = BACKGROUND_BLIT), require = 2)
	private void lampas2$trackEffectBackground(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
		int x, int y, int width, int height, Operation<Void> original) {
		EffectIconPosition.reset();
		EffectIconPosition.beginBackground();
		try {
			original.call(graphics, pipeline, sprite, x, y, width, height);
		} finally {
			EffectIconPosition.endBackground();
		}
	}
}
