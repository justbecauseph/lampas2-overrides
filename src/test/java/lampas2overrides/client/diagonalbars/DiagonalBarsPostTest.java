package lampas2overrides.client.diagonalbars;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import net.minecraft.client.renderer.block.dispatch.multipart.CombinedCondition;
import net.minecraft.client.renderer.block.dispatch.multipart.Condition;
import net.minecraft.client.renderer.block.dispatch.multipart.KeyValueCondition;
import net.minecraft.world.level.block.state.BlockState;

class DiagonalBarsPostTest {

	private static final KeyValueCondition.Terms FALSE = KeyValueCondition.Terms.parse("false").getOrThrow();

	/** The rotated copy Diagonal Blocks would add. Any instance other than NEVER stands for it. */
	private static final Condition ROTATED = keyValue("east");

	/** A key-value condition with every given key set to {@code false}, as the vanilla blockstates write them. */
	private static KeyValueCondition keyValue(String... keys) {
		Map<String, KeyValueCondition.Terms> tests = new HashMap<>();
		for (String key : keys) {
			tests.put(key, FALSE);
		}
		return new KeyValueCondition(tests);
	}

	/** The vanilla iron_bars post selector: east, north, south and west all false, selecting template_bars_post. */
	@Test
	void barsPostSelectorIsAnUnconnectedPostAndItsRotatedCopyIsReplaced() {
		Optional<Condition> post = Optional.of(keyValue("east", "north", "south", "west"));

		assertTrue(DiagonalBarsPost.isUnconnectedPost(post));
		assertSame(DiagonalBarsPost.NEVER, DiagonalBarsPost.rotatedCondition(ROTATED, post));
	}

	/** The vanilla glass_pane noside selector names one direction, so its rotated copy must stay. */
	@Test
	void singleDirectionSelectorIsNotAPostAndKeepsItsRotatedCopy() {
		Optional<Condition> noside = Optional.of(keyValue("north"));

		assertFalse(DiagonalBarsPost.isUnconnectedPost(noside));
		assertSame(ROTATED, DiagonalBarsPost.rotatedCondition(ROTATED, noside));
	}

	@Test
	void missingConditionIsNotAPostAndKeepsItsRotatedCopy() {
		assertFalse(DiagonalBarsPost.isUnconnectedPost(Optional.empty()));
		assertSame(ROTATED, DiagonalBarsPost.rotatedCondition(ROTATED, Optional.empty()));
	}

	/** Two halves that together name all four directions are still a combined condition, not one key-value condition. */
	@Test
	void combinedConditionIsNotAPostAndKeepsItsRotatedCopy() {
		Condition halves = new CombinedCondition(CombinedCondition.Operation.AND,
			List.of(keyValue("east", "north"), keyValue("south", "west")));

		assertFalse(DiagonalBarsPost.isUnconnectedPost(Optional.of(halves)));
		assertSame(ROTATED, DiagonalBarsPost.rotatedCondition(ROTATED, Optional.of(halves)));
	}

	/** NEVER has no terms, so it never reads the state definition and no state can satisfy it. */
	@Test
	void neverConditionMatchesNoBlockState() {
		Predicate<BlockState> never = DiagonalBarsPost.NEVER.instantiate(null);

		assertFalse(never.test(null));
	}
}
