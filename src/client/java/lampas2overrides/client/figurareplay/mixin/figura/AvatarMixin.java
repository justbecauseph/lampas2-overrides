package lampas2overrides.client.figurareplay.mixin.figura;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;

/**
 * Records the pings that carry an avatar's script state.
 *
 * <p>A toggled animation is not part of the avatar: it is a Lua variable the owner flips with a
 * keybind and broadcasts with {@code pings.setSomething(state)}. That broadcast goes over Figura's
 * backend, so a replay stores the avatar but none of the state, and every toggle plays back off.
 *
 * <p>{@code runPing} is the one place both halves meet — pings received from other players arrive
 * here from the websocket handler, and the owner's own arrive here from {@code PingFunction} — so
 * recording here captures exactly the state the recording client was rendering, its own included.
 */
@Mixin(targets = "org.figuramc.figura.avatar.Avatar", remap = false)
public class AvatarMixin {

	@Inject(method = "runPing(I[B)V", at = @At("HEAD"))
	private void lampas2$recordPing(int id, byte[] data, CallbackInfo ci) {
		FiguraReplayBridge.onPing(this, id, data);
	}
}
