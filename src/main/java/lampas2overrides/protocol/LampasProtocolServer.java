package lampas2overrides.protocol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

public class LampasProtocolServer implements ModInitializer {

	private static String getServerPackVersion() {
		String sysProp = System.getProperty("lampas.pack_version");
		if (sysProp != null && !sysProp.isBlank()) {
			return sysProp.trim();
		}
		String envProp = System.getenv("LAMPAS_PACK_VERSION");
		if (envProp != null && !envProp.isBlank()) {
			return envProp.trim();
		}
		return LampasProtocol.DEFAULT_PACK_VERSION;
	}

	@Override
	public void onInitialize() {
		LampasProtocol.LOGGER.info("[LampasCore] Initializing Protocol Mod v{} (Pack Target: {})",
				LampasProtocol.PROTOCOL_VERSION, getServerPackVersion());

		// 1. Register Custom Payload Codecs
		PayloadTypeRegistry.serverboundPlay().register(HandshakeC2SPayload.TYPE, HandshakeC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HandshakeS2CPayload.TYPE, HandshakeS2CPayload.STREAM_CODEC);

		// 2. Register Server Receiver for Client Handshake
		ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.TYPE, (payload, context) -> {
			String clientVersion = payload.packVersion();
			int clientProtocol = payload.protocolVersion();
			String serverVersion = getServerPackVersion();

			LampasProtocol.LOGGER.info("[LampasCore] Player '{}' connected with Lampas pack v{} (protocol {})",
					context.player().getName().getString(), clientVersion, clientProtocol);

			context.server().execute(() -> {
				if (!serverVersion.equalsIgnoreCase(clientVersion) || clientProtocol != LampasProtocol.PROTOCOL_VERSION) {
					Component kickReason = Component.literal(
							"§c§l[Lampas SMP] Modpack Update Required\n\n"
									+ "§7Your Client Version: §c" + clientVersion + "\n"
									+ "§7Server Version:        §a" + serverVersion + "\n\n"
									+ "§ePlease close Minecraft and launch the §6Lampas Launcher §eto auto-update your modpack!"
					);

					LampasProtocol.LOGGER.warn("[LampasCore] Rejecting player '{}': version mismatch (Client: {}, Server: {})",
							context.player().getName().getString(), clientVersion, serverVersion);

					context.player().connection.disconnect(kickReason);
				} else {
					ServerPlayNetworking.send(context.player(), new HandshakeS2CPayload(
							true,
							serverVersion,
							LampasProtocol.PROTOCOL_VERSION,
							"Lampas pack verified successfully."
					));
				}
			});
		});
	}
}
