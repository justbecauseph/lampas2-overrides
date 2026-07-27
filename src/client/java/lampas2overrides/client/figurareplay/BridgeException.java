package lampas2overrides.client.figurareplay;

/** Thrown when a Figura or ReplayMod member the bridge already resolved fails at the call site. */
final class BridgeException extends RuntimeException {

	BridgeException(String message, Throwable cause) {
		super(message, cause);
	}

	BridgeException(String message) {
		super(message);
	}
}
