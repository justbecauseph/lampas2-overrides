package lampas2overrides.lootrtickgate;

/** Lootr's own server-side tick schedule for one container instance. No Lootr or Minecraft types. */
public final class LootrTickGate {
	private LootrTickGate() {}

	/**
	 * True when Lootr 1.24.41.124's ILootrAPI#handleInstanceTick would return after fetching the
	 * container's data without using it: currentTicks % (tickDelay + randomOffset) != 0.
	 * A zero period is left to Lootr (false), which throws ArithmeticException exactly as before.
	 */
	public static boolean skipsTick(int currentTicks, int tickDelay, int randomOffset) {
		int period = tickDelay + randomOffset;
		return period != 0 && currentTicks % period != 0;
	}
}
