package lampas2overrides.protocol;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LampasProtocol {

	public static final String MOD_ID = "lampas-core";
	public static final Logger LOGGER = LoggerFactory.getLogger("LampasCore");

	public static final int PROTOCOL_VERSION = 1;
	public static final String DEFAULT_PACK_VERSION = "2.0.0";

	public static final Identifier HANDSHAKE_C2S_ID = Identifier.fromNamespaceAndPath("lampas", "handshake_c2s");
	public static final Identifier HANDSHAKE_S2C_ID = Identifier.fromNamespaceAndPath("lampas", "handshake_s2c");

	private LampasProtocol() {
	}
}
