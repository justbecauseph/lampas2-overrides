package lampas2overrides.client.protocol;

import lampas2overrides.protocol.HandshakeC2SPayload;
import lampas2overrides.protocol.HandshakeS2CPayload;
import lampas2overrides.protocol.LampasProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class LampasProtocolClient implements ClientModInitializer {

	private static String getClientPackVersion() {
		String sysProp = System.getProperty("lampas.pack_version");
		if (sysProp != null && !sysProp.isBlank()) {
			return sysProp.trim();
		}
		return LampasProtocol.DEFAULT_PACK_VERSION;
	}

	@Override
	public void onInitializeClient() {
		LampasProtocol.LOGGER.info("[LampasCore] Client Protocol Initialized (Pack v{})", getClientPackVersion());

		// 1. Send Handshake C2S when connecting to server
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			String packVersion = getClientPackVersion();
			LampasProtocol.LOGGER.info("[LampasCore] Sending Lampas handshake packet (v{}, proto {})...",
					packVersion, LampasProtocol.PROTOCOL_VERSION);

			ClientPlayNetworking.send(new HandshakeC2SPayload(
					packVersion,
					LampasProtocol.PROTOCOL_VERSION,
					"LampasLauncher-2.0.0"
			));
		});

		// 2. Receive Handshake S2C from server
		ClientPlayNetworking.registerGlobalReceiver(HandshakeS2CPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (payload.accepted()) {
					LampasProtocol.LOGGER.info("[LampasCore] Handshake accepted by server (Server Pack v{}, proto {})",
							payload.serverPackVersion(), payload.serverProtocolVersion());
				}
			});
		});
	}
}
