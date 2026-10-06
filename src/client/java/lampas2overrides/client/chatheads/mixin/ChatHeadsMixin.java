package lampas2overrides.client.chatheads.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.client.chatheads.ChatPlayerResolver;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Resolves custom/TAB display names before Chatting's token-based sender detector runs. */
@Mixin(targets = "org.polyfrost.chatting.chat.ChatHeads", remap = false)
public class ChatHeadsMixin {

	@Inject(
			method = "detect(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/PlayerInfo;",
			at = @At("HEAD"),
			cancellable = true)
	private void lampas2$detectDisplayName(String message, CallbackInfoReturnable<PlayerInfo> cir) {
		ChatPlayerResolver.Resolution result = ChatPlayerResolver.resolve(message);
		switch (result.type()) {
			case MATCH -> cir.setReturnValue(result.player());
			case AMBIGUOUS -> cir.setReturnValue(null);
			case NO_MATCH -> {}
		}
	}
}

