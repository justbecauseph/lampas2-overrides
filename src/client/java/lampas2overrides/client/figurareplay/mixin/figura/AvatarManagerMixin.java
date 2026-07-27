package lampas2overrides.client.figurareplay.mixin.figura;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;

/**
 * Keeps the backend from overwriting the avatars a replay carries.
 *
 * <p>{@code getAvatarForPlayer} funnels through {@code fetchBackend}, which would ask Figura's
 * backend what that player is wearing <em>now</em> and replace the recorded avatar with the answer.
 * Cancelling leaves the replay's own data in place and, incidentally, lets replays play back with
 * no network at all.
 *
 * <p>Players the replay has no data for still fall through, so a replay recorded without this
 * bridge behaves exactly as it did before.
 */
@Mixin(targets = "org.figuramc.figura.avatar.AvatarManager", remap = false)
public class AvatarManagerMixin {

	@Inject(method = "fetchBackend(Ljava/util/UUID;)V", at = @At("HEAD"), cancellable = true)
	private static void lampas2$preferRecordedAvatar(UUID id, CallbackInfo ci) {
		if (FiguraReplayBridge.hasRecordedAvatar(id)) {
			ci.cancel();
		}
	}
}
