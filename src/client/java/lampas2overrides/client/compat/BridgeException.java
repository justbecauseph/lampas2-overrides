package lampas2overrides.client.compat;

/** Thrown when a Figura or ReplayMod member the bridge already resolved fails at the call site. */
public final class BridgeException extends RuntimeException {

	public BridgeException(String message, Throwable cause) {
		super(message, cause);
	}

	public BridgeException(String message) {
		super(message);
	}
}
