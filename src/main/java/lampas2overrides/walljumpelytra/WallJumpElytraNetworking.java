package lampas2overrides.walljumpelytra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers the serverbound {@link StopFallFlyingC2SPayload} channel when Wall-Jump TXF is installed, allowing the
 * client to cancel elytra flight on wall grab without clinging.
 */
public final class WallJumpElytraNetworking implements ModInitializer {

	public static final String WALL_JUMP_MOD_ID = "walljump";

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/wall-jump-elytra");

	private static volatile boolean registered;

	@Override
	public void onInitialize() {
		if (!FabricLoader.getInstance().isModLoaded(WALL_JUMP_MOD_ID)) {
			return;
		}
		PayloadTypeRegistry.serverboundPlay().register(
			StopFallFlyingC2SPayload.TYPE,
			StopFallFlyingC2SPayload.STREAM_CODEC
		);
		ServerPlayNetworking.registerGlobalReceiver(
			StopFallFlyingC2SPayload.TYPE,
			(payload, context) -> context.server().execute(() -> handleStopFallFlying(context.player()))
		);
		registered = true;
		LOGGER.info("Registered Wall-Jump TXF elytra-cancel packet receiver");
	}

	public static boolean isRegistered() {
		return registered;
	}

	/** Cancels fall-flying on the server when the player is gliding; preserves fallDistance since no cling occurred. */
	public static boolean handleStopFallFlying(ServerPlayer player) {
		if (player == null || !player.isFallFlying()) {
			return false;
		}
		player.stopFallFlying();
		return true;
	}
}
