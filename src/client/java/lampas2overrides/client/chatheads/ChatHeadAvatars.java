package lampas2overrides.client.chatheads;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Draws the Figura avatar's face in Chatting's chat heads.
 *
 * <p>Chatting draws a chat head from the player's <em>skin</em> texture, so a player wearing a
 * Figura avatar shows up in chat as their vanilla face — not the character everyone can see in the
 * world. Figura already substitutes its avatars for skin faces in the tab list and the permissions
 * screen; this extends the same treatment to chat.
 *
 * <p>The two halves are deliberately hooked apart. Chatting resolves the owner of a chat line
 * ({@code ChatHeads#lookup}) immediately before drawing its face, so that call arms this class with
 * whose avatar is about to be wanted; the face hooks then consume it. Going through the owner
 * rather than the skin texture avoids having to map textures back to players, and keeps the face
 * hooks from touching faces drawn anywhere else.
 */
public final class ChatHeadAvatars {

	static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/chat-heads");

	private static final String FIGURA = "figura";
	private static final String CHATTING = "chatting";

	private static boolean enabled;

	/**
	 * The player whose chat head is about to be drawn, or {@code null}.
	 *
	 * <p>Client thread only — chat rendering and Chatting's lookup both run there. Armed and
	 * consumed within a single head draw, and re-armed (usually to {@code null}) by the very next
	 * line's lookup, so a stale value cannot survive to a later frame.
	 */
	private static UUID pendingOwner;

	private ChatHeadAvatars() {
	}

	public static void init() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded(FIGURA) || !loader.isModLoaded(CHATTING)) {
			return;
		}

		String missing = FiguraPortraits.bind();
		if (missing != null) {
			LOGGER.error("Figura chat heads disabled: cannot resolve {}", missing);
			return;
		}

		enabled = true;
		LOGGER.info("Figura avatars in Chatting's chat heads active");
	}

	/** Records whose head Chatting is about to draw. Called with its {@code lookup} result. */
	public static void arm(PlayerInfo owner) {
		if (!enabled) {
			return;
		}
		pendingOwner = owner == null ? null : owner.getProfile().id();
	}

	/** Clears the armed owner when Chatting decides not to draw a head after all. */
	public static void disarm() {
		pendingOwner = null;
	}

	/**
	 * Draws the armed player's Figura face, if they have one.
	 *
	 * @return whether the caller should skip its own face draw
	 */
	public static boolean draw(GuiGraphicsExtractor graphics, int x, int y, int size, boolean upsideDown) {
		UUID owner = pendingOwner;
		pendingOwner = null;

		if (!enabled || owner == null) {
			return false;
		}

		try {
			Object avatar = FiguraPortraits.avatarFor(owner);
			return avatar != null && FiguraPortraits.renderPortrait(avatar, graphics, x, y, size, upsideDown);
		} catch (Throwable t) {
			enabled = false;
			LOGGER.error("Disabling Figura chat heads after an unexpected failure", t);
			return false;
		}
	}
}
