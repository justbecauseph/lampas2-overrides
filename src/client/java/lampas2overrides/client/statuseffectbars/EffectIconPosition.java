package lampas2overrides.client.statuseffectbars;

/**
 * Remembers where vanilla's status effect background was actually blitted, so Status Effect Bars can draw under it.
 *
 * <p>VanillaHUD wraps the two background {@code blitSprite} calls in {@code Hud#extractEffects} and shifts their x/y
 * arguments, but the method's locals stay where vanilla put them. Status Effect Bars reads those locals when it draws
 * its duration bar, so the bar sat where vanilla would have put the icon. The mixins in this package bracket each
 * background blit with {@link #beginBackground()} and {@link #endBackground()}, and call {@link #record(int, int)} at
 * the head of {@code GuiGraphicsExtractor#blitSprite}. That call sees the arguments after every wrapper has applied
 * its shift, whatever order the wrappers nest in. {@link #x(int)} and {@link #y(int)} return the recorded position,
 * or the fallback when none was recorded.
 *
 * <p>Render thread only. The state is plain static fields with no synchronisation and no Minecraft types.
 */
public final class EffectIconPosition {

	private static boolean capturing;
	private static boolean recorded;
	private static int x;
	private static int y;

	private EffectIconPosition() {
	}

	/** Clears the recorded position and closes the capture window. */
	public static void reset() {
		capturing = false;
		recorded = false;
	}

	/** Opens the capture window for one background blit. */
	public static void beginBackground() {
		capturing = true;
	}

	/** Closes the capture window. The recorded position stays until the next {@link #reset()}. */
	public static void endBackground() {
		capturing = false;
	}

	/** Records a blit's position if a capture window is open. Only the first blit in a window counts. */
	public static void record(int drawnX, int drawnY) {
		if (capturing) {
			x = drawnX;
			y = drawnY;
			recorded = true;
			capturing = false;
		}
	}

	/** Returns the recorded x, or {@code fallback} when no background blit has been recorded. */
	public static int x(int fallback) {
		return recorded ? x : fallback;
	}

	/** Returns the recorded y, or {@code fallback} when no background blit has been recorded. */
	public static int y(int fallback) {
		return recorded ? y : fallback;
	}
}
