package lampas2overrides;

/**
 * Constants shared across the mod's source sets.
 *
 * <p>There is no {@code main} entrypoint: everything this mod does is client-side, and
 * {@code fabric.mod.json} declares it as such. This lives in the common source set so the id has one
 * definition rather than one per place that spells it out.
 */
public final class Lampas2Overrides {

	public static final String MOD_ID = "lampas2-overrides";

	private Lampas2Overrides() {
	}
}
