package lampas2overrides.client.chatheads.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.client.chatheads.ChatHeadAvatars;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.resources.Identifier;

/**
 * Substitutes the Figura face for the skin face when the head being drawn is a chat head.
 *
 * <p>Both of Chatting's paths land here. Its default path uses vanilla
 * {@code extractRenderState}, which every shorter overload funnels into; its "improved heads"
 * option instead calls {@code chatting$draw}, which Chatting adds to this same class and which
 * blits the face itself rather than going through the vanilla extractor. Hooking only one would
 * leave the other drawing skin faces.
 *
 * <p>Applied at a raised priority so that Chatting's mixin has already contributed
 * {@code chatting$draw} by the time this one is applied. That injection is {@code require = 0}: the
 * method is Chatting's internal detail, and a cosmetic feature losing one of its two paths is a
 * better outcome than refusing to start the game.
 */
@Mixin(value = PlayerFaceExtractor.class, priority = 1500)
public class PlayerFaceExtractorMixin {

	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V",
			at = @At("HEAD"),
			cancellable = true)
	private static void lampas2$figuraChatHead(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
			int size, boolean hasHatLayer, boolean upsideDown, int color, CallbackInfo ci) {
		if (ChatHeadAvatars.draw(graphics, x, y, size, upsideDown)) {
			ci.cancel();
		}
	}

	@Inject(
			method = "chatting$draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIIZZ)V",
			at = @At("HEAD"),
			cancellable = true,
			remap = false,
			require = 0)
	private void lampas2$figuraChatHeadImproved(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
			int size, int color, boolean hasHatLayer, boolean upsideDown, CallbackInfo ci) {
		if (ChatHeadAvatars.draw(graphics, x, y, size, upsideDown)) {
			ci.cancel();
		}
	}
}
