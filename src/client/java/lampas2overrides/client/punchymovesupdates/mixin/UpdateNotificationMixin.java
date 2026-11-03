package lampas2overrides.client.punchymovesupdates.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Punchy! Moves from asking Modrinth for a newer release.
 *
 * <p>Punchy! Moves' client entrypoint calls {@code UpdateNotification#initialize}, which starts a background request to the
 * Modrinth API and, when it finds a newer release, draws a clickable toast linking to Modrinth on every screen. It has no
 * setting for this. The pack pins the version, so the check is cancelled before it starts; with no result, the toast never
 * draws.
 */
@Mixin(targets = "punchymoves.client.update.UpdateNotification", remap = false)
public class UpdateNotificationMixin {

	@Inject(method = "initialize(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
	private static void lampas2$skipUpdateCheck(String installed, String gameVersion, String loader, CallbackInfo ci) {
		ci.cancel();
	}
}
