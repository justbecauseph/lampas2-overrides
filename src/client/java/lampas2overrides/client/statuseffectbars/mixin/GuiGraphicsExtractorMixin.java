package lampas2overrides.client.statuseffectbars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import lampas2overrides.client.statuseffectbars.EffectIconPosition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Records the x/y that the six-argument {@code blitSprite} is actually drawn at, while {@link HudEffectsMixin} has a
 * capture window open.
 *
 * <p>The head of the method receives the arguments after every caller-side wrapper has shifted them, so the record
 * is the drawn position whatever order VanillaHUD's wrap and this wrap nest in. Outside an effect background window,
 * {@link EffectIconPosition#record(int, int)} does nothing.
 */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {

	@Inject(
		method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
		at = @At("HEAD")
	)
	private void lampas2$recordEffectBackground(RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
		int height, CallbackInfo ci) {
		EffectIconPosition.record(x, y);
	}
}
