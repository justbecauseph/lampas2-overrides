package lampas2overrides.client.statuseffectbars;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectIconPositionTest {

	@BeforeEach
	void resetState() {
		EffectIconPosition.reset();
	}

	@Test
	void returnsTheFallbackWhenNothingWasRecorded() {
		assertEquals(475, EffectIconPosition.x(475));
		assertEquals(27, EffectIconPosition.y(27));
	}

	@Test
	void returnsTheBlitPositionRecordedInsideABackgroundWindow() {
		EffectIconPosition.beginBackground();
		EffectIconPosition.record(471, 1);
		EffectIconPosition.endBackground();

		assertEquals(471, EffectIconPosition.x(475));
		assertEquals(1, EffectIconPosition.y(27));
	}

	@Test
	void ignoresBlitsOutsideABackgroundWindow() {
		EffectIconPosition.record(471, 1);
		assertEquals(475, EffectIconPosition.x(475));
		assertEquals(27, EffectIconPosition.y(27));

		EffectIconPosition.beginBackground();
		EffectIconPosition.endBackground();
		EffectIconPosition.record(471, 1);
		assertEquals(475, EffectIconPosition.x(475));
		assertEquals(27, EffectIconPosition.y(27));
	}

	@Test
	void keepsOnlyTheFirstBlitInAWindow() {
		EffectIconPosition.beginBackground();
		EffectIconPosition.record(471, 1);
		EffectIconPosition.record(300, 50);
		EffectIconPosition.endBackground();

		assertEquals(471, EffectIconPosition.x(475));
		assertEquals(1, EffectIconPosition.y(27));
	}

	@Test
	void resetClearsTheRecordedPosition() {
		EffectIconPosition.beginBackground();
		EffectIconPosition.record(471, 1);
		EffectIconPosition.endBackground();
		EffectIconPosition.reset();

		assertEquals(475, EffectIconPosition.x(475));
		assertEquals(27, EffectIconPosition.y(27));
	}
}
