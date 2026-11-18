package lampas2overrides.client.signsfaster;

import net.minecraft.network.chat.Component;

/** Decides whether a sign has no text on any line. Holds no Signs Faster types. */
public final class SignsFasterBlankCheck {

	private SignsFasterBlankCheck() {
	}

	/** Returns true when every line's {@code getString()} is empty. */
	public static boolean allLinesEmpty(Component[] lines) {
		for (Component line : lines) {
			if (!line.getString().isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
