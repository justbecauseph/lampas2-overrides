package lampas2overrides.lootrtickgate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Expected values follow Lootr 1.24.41.124: a container acts only when currentTicks % (tickDelay + offset) == 0. */
class LootrTickGateTest {

	@Test
	void actsOnlyOnTheScheduledTicksWithAnOffset() {
		assertFalse(LootrTickGate.skipsTick(0, 20, 5));
		assertFalse(LootrTickGate.skipsTick(25, 20, 5));
		assertFalse(LootrTickGate.skipsTick(50, 20, 5));
		assertTrue(LootrTickGate.skipsTick(1, 20, 5));
		assertTrue(LootrTickGate.skipsTick(24, 20, 5));
		assertTrue(LootrTickGate.skipsTick(26, 20, 5));
	}

	@Test
	void actsOnlyOnTheScheduledTicksWithoutAnOffset() {
		assertFalse(LootrTickGate.skipsTick(40, 20, 0));
		assertTrue(LootrTickGate.skipsTick(41, 20, 0));
	}

	@Test
	void skipsWhenLootrReadsNoServerTickCount() {
		// Lootr's getCurrentTicks returns -1 without a server, and -1 % 25 != 0, so Lootr also returns on that tick.
		assertTrue(LootrTickGate.skipsTick(-1, 20, 5));
	}

	@Test
	void leavesAZeroPeriodToLootr() {
		assertFalse(LootrTickGate.skipsTick(7, 0, 0));
	}
}
