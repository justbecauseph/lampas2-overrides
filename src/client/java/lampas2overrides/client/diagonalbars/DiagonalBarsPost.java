package lampas2overrides.client.diagonalbars;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.renderer.block.dispatch.multipart.CombinedCondition;
import net.minecraft.client.renderer.block.dispatch.multipart.Condition;
import net.minecraft.client.renderer.block.dispatch.multipart.KeyValueCondition;

/**
 * Decides whether a Diagonal Blocks rotated copy must be replaced. A bar-style post selects on all four cardinal keys
 * being {@code false}, and Diagonal Blocks 26.2.0 also adds a rotated copy of that post. The copy lies in the plane of the
 * two rotated arms and z-fights with them on straight north-east/south-west runs, where vanilla draws no post at all. A
 * glass pane's {@code noside} selector names one direction and keeps its rotated copy. Holds no Diagonal Blocks types.
 */
public final class DiagonalBarsPost {

	private static final Set<String> CARDINALS = Set.of("north", "east", "south", "west");

	/** Never matches any block state: an OR over no terms. */
	static final Condition NEVER = new CombinedCondition(CombinedCondition.Operation.OR, List.of());

	private DiagonalBarsPost() {
	}

	/** Returns true when the condition is a key-value condition that names all four cardinal directions. */
	public static boolean isUnconnectedPost(Optional<Condition> condition) {
		return condition.isPresent()
			&& condition.get() instanceof KeyValueCondition keyValue
			&& keyValue.tests().keySet().containsAll(CARDINALS);
	}

	/** Returns {@link #NEVER} for a bar-style post's rotated copy, and the rotated condition otherwise. */
	public static Condition rotatedCondition(Condition rotated, Optional<Condition> source) {
		return isUnconnectedPost(source) ? NEVER : rotated;
	}
}
