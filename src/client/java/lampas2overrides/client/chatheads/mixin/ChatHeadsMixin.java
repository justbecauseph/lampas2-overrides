package lampas2overrides.client.chatheads.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.client.chatheads.ChatHeadAvatars;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.FormattedCharSequence;

/**
 * Picks up whose chat head Chatting is about to draw.
 *
 * <p>Chatting's head drawing calls {@code lookup} to find the line's owner, then
 * {@code shouldDrawHead} to decide, then draws — all back to back. Arming on the first and
 * releasing on a negative second leaves the owner set only across the draw itself.
 *
 * <p>{@code ChatHeads} is an ordinary Kotlin object, not one of Chatting's mixins, so this does not
 * depend on mixin application order.
 */
@Mixin(targets = "org.polyfrost.chatting.chat.ChatHeads", remap = false)
public class ChatHeadsMixin {

	@Inject(
			method = "lookup(Lnet/minecraft/util/FormattedCharSequence;)Lnet/minecraft/client/multiplayer/PlayerInfo;",
			at = @At("RETURN"))
	private void lampas2$armChatHead(FormattedCharSequence content, CallbackInfoReturnable<PlayerInfo> cir) {
		ChatHeadAvatars.arm(cir.getReturnValue());
	}

	@Inject(
			method = "shouldDrawHead(Lnet/minecraft/client/multiplayer/PlayerInfo;Z)Z",
			at = @At("RETURN"))
	private void lampas2$releaseUndrawnHead(PlayerInfo info, boolean hidden, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			ChatHeadAvatars.disarm();
		}
	}
}
