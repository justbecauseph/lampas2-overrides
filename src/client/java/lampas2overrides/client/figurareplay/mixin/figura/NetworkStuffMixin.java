package lampas2overrides.client.figurareplay.mixin.figura;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;

/**
 * Stops Figura subscribing to the players in a replay's tab list.
 *
 * <p>Subscriptions exist so the backend can push live avatar changes and pings. Applying either to
 * a recording is wrong — a player reloading their avatar right now would visibly swap it in the
 * middle of footage recorded last week — and the traffic buys nothing besides.
 */
@Mixin(targets = "org.figuramc.figura.backend2.NetworkStuff", remap = false)
public class NetworkStuffMixin {

	@Inject(method = "tickSubscriptions()V", at = @At("HEAD"), cancellable = true)
	private static void lampas2$noSubscriptionsDuringReplay(CallbackInfo ci) {
		if (FiguraReplayBridge.isReplayActive()) {
			ci.cancel();
		}
	}
}
