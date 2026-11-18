package lampas2overrides.client.voxychat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import me.cortex.voxy.common.Logger;

/**
 * Voxy's {@code Logger.error} logs each error and then posts it to chat through {@code showInHUD}, with no setting to
 * turn the chat copy off. Render-thread races such as {@code NodeManager}'s "its type was a request, ignoring!" fill
 * players' chat. This drops only that call: the error still reaches {@code latest.log}, and Voxy's own direct
 * {@code showInHUD} callers ({@code DebugUtils}) are unchanged.
 */
@Mixin(value = Logger.class, remap = false)
abstract class VoxyLoggerMixin {

	@WrapWithCondition(method = "error",
		at = @At(value = "INVOKE", target = "Lme/cortex/voxy/common/Logger;showInHUD(Ljava/lang/String;)V"))
	private static boolean lampas2$keepErrorsOutOfChat(String message) {
		return false;
	}
}
