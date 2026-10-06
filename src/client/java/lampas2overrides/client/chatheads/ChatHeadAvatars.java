package lampas2overrides.client.chatheads;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Draws Figura portraits at Chatting's current head-face draw boundary. */
public final class ChatHeadAvatars {

	static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/chat-heads");

	private static final String FIGURA = "figura";
	private static final String CHATTING = "chatting";
	static final int VANILLA_FACE_SIZE = 8;

	private static boolean enabled;
	private static final ThreadLocal<ChatHeadDrawContext> CURRENT_DRAW = new ThreadLocal<>();

	private ChatHeadAvatars() {
	}

	public static void init() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded(FIGURA) || !loader.isModLoaded(CHATTING)) {
			return;
		}

		String missing = FiguraPortraits.bind();
		if (missing == null) {
			missing = ChattingHeadTextures.bind();
		}
		if (missing != null) {
			LOGGER.error("Figura chat heads disabled: cannot resolve {}", missing);
			return;
		}

		enabled = true;
		LOGGER.info("Figura avatars in Chatting's chat heads active");
	}

	/** Starts the scope around Chatting's complete draw call. */
	public static void beginDraw(PlayerInfo owner, int x) {
		CURRENT_DRAW.remove();
		if (!enabled || owner == null) {
			return;
		}

		try {
			UUID ownerId = owner.getProfile().id();
			Object avatar = FiguraPortraits.avatarFor(ownerId);
			if (avatar != null) {
				CURRENT_DRAW.set(new ChatHeadDrawContext(ownerId, x, avatar));
			}
		} catch (Throwable t) {
			disableAfterFailure("looking up a chat-head avatar", t);
		}
	}

	/** Ends the draw scope even when Chatting throws while producing a head. */
	public static void endDraw() {
		CURRENT_DRAW.remove();
	}

	/**
	 * Draws the active owner's avatar in place of one of Chatting's shadow/main face calls.
	 *
	 * @return whether Chatting should cancel this face call
	 */
	public static boolean drawFace(GuiGraphicsExtractor graphics, PlayerInfo owner, Object head, int x, int y) {
		ChatHeadDrawContext context = CURRENT_DRAW.get();
		if (!enabled || context == null || owner == null) {
			return false;
		}

		ChatHeadDrawContext.FacePlan plan = context.plan(owner.getProfile().id(), x, y);
		if (plan.action() == ChatHeadDrawContext.FaceAction.PASS_THROUGH) {
			return false;
		}
		if (plan.action() == ChatHeadDrawContext.FaceAction.SUPPRESS_DUPLICATE) {
			return true;
		}

		try {
			int size = head == null ? VANILLA_FACE_SIZE : ChattingHeadTextures.getSize(head);
			if (FiguraPortraits.renderPortrait(context.avatar(), graphics, plan.x(), plan.y(), size, false)) {
				context.markAvatarDrawn();
				return true;
			}
		} catch (Throwable t) {
			disableAfterFailure("drawing a chat-head avatar", t);
		}
		return false;
	}

	private static void disableAfterFailure(String operation, Throwable failure) {
		enabled = false;
		CURRENT_DRAW.remove();
		LOGGER.error("Disabling Figura chat heads after failing while {}", operation, failure);
	}
}
