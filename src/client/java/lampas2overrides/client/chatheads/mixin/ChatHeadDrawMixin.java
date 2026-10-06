package lampas2overrides.client.chatheads.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import lampas2overrides.client.chatheads.ChatHeadAvatars;
import lampas2overrides.client.chatheads.ChattingHeadContract;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Uses Chatting's current PlayerInfo-bearing draw path and intercepts both cached and fallback faces. */
@Mixin(targets = "org.polyfrost.chatting.chat.ChatHeads", remap = false)
public abstract class ChatHeadDrawMixin {

	@WrapMethod(method = ChattingHeadContract.DRAW_METHOD)
	private void lampas2$scopeChatHeadDraw(GuiGraphicsExtractor graphics, PlayerInfo owner, int x, int y, int color,
			Operation<Void> original) {
		ChatHeadAvatars.beginDraw(owner, x);
		try {
			original.call(graphics, owner, x, y, color);
		} finally {
			ChatHeadAvatars.endDraw();
		}
	}

	@Inject(method = ChattingHeadContract.DRAW_FACE_METHOD, at = @At("HEAD"), cancellable = true)
	private void lampas2$drawAvatarFace(GuiGraphicsExtractor graphics, PlayerInfo owner, @Coerce Object head,
			int x, int y, int color, CallbackInfo ci) {
		if (ChatHeadAvatars.drawFace(graphics, owner, head, x, y)) {
			ci.cancel();
		}
	}
}
