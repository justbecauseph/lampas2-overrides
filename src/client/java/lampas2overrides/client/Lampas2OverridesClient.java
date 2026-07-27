package lampas2overrides.client;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;
import net.fabricmc.api.ClientModInitializer;

public class Lampas2OverridesClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		FiguraReplayBridge.init();
	}
}
