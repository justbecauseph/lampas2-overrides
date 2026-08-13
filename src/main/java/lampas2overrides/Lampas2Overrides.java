package lampas2overrides;

/**
 * Constants shared across the mod's source sets.
 *
 * <p>There is no {@code main} entrypoint: the common compatibility features are installed through
 * gated mixin configs, while client initialization remains in the client source set. This class
 * keeps the mod id in one place.
 */
public final class Lampas2Overrides {

	public static final String MOD_ID = "lampas2-overrides";

	private Lampas2Overrides() {
	}
}
