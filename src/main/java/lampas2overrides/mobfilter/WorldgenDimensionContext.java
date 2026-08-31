package lampas2overrides.mobfilter;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Scopes the active dimension identifier across worldgen entity placement.
 */
public final class WorldgenDimensionContext {

	private static final ThreadLocal<Identifier> CURRENT_DIMENSION = new ThreadLocal<>();

	private WorldgenDimensionContext() {
	}

	/**
	 * Returns the active worldgen dimension identifier, or {@code null} if world generation is
	 * not currently placing entities under {@link lampas2overrides.mobfilter.mixin.MixinServiceMixin}.
	 */
	@Nullable
	public static Identifier get() {
		return CURRENT_DIMENSION.get();
	}

	/**
	 * Sets the active worldgen dimension identifier, or clears the thread-local state if {@code null}.
	 */
	public static void set(@Nullable Identifier identifier) {
		if (identifier == null) {
			CURRENT_DIMENSION.remove();
		} else {
			CURRENT_DIMENSION.set(identifier);
		}
	}

	/**
	 * Clears the thread-local dimension state for the current thread.
	 */
	public static void clear() {
		CURRENT_DIMENSION.remove();
	}
}
