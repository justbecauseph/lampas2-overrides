package lampas2overrides.client.hotbarsync.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

/**
 * Forgets the last hotbar slot sent to the server whenever a new local player is created.
 *
 * <p>{@code ensureHasSentCarriedItem} sends {@code ServerboundSetCarriedItemPacket} only when the selected slot
 * differs from {@code carriedIndex}, and nothing else writes that field. Respawn keeps the same
 * {@link MultiPlayerGameMode}. Vanilla's new player starts on slot 0, which differs from the stale index, so the
 * first tick resyncs. Debugify's MC-143474 fix instead copies the old selected slot onto the new player in
 * {@code handleRespawn}; that equals the stale index, so no packet is sent and the server's new player stays on
 * slot 0 while the client shows the pre-death item. An impossible value makes the next tick send the real slot.
 * {@code handleRespawn} creates the player before Debugify's injection point, and the three-argument overload
 * delegates here, so login and respawn both pass through. Deliberately not gated on Debugify or any version.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

	@Shadow
	private int carriedIndex;

	@Inject(
		method = "createPlayer(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/stats/StatsCounter;Lnet/minecraft/client/ClientRecipeBook;Lnet/minecraft/world/entity/player/Input;Z)Lnet/minecraft/client/player/LocalPlayer;",
		at = @At("RETURN")
	)
	private void lampas2$resendCarriedItem(CallbackInfoReturnable<LocalPlayer> cir) {
		carriedIndex = -1;
	}
}
