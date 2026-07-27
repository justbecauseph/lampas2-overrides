package lampas2overrides.client.chatheads;

import java.lang.reflect.Method;
import java.util.UUID;

import lampas2overrides.client.compat.Reflection;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The slice of Figura needed to draw an avatar's face, resolved reflectively.
 *
 * <p>Figura already renders avatar faces in place of skin faces for the tab list and the
 * permissions screen, through {@code Avatar#renderPortrait}. This reuses that same entry point, so
 * a chat head looks like the face Figura draws everywhere else rather than a second, subtly
 * different implementation.
 *
 * <p>Bound separately from the replay bridge's {@code FiguraApi}: the two features need different
 * members and different companion mods, and neither should be disabled by the other's absence.
 */
final class FiguraPortraits {

	private static final String AVATAR_MANAGER = "org.figuramc.figura.avatar.AvatarManager";
	private static final String AVATAR = "org.figuramc.figura.avatar.Avatar";

	private static Method getAvatarForPlayer;
	private static Method renderPortrait;

	private FiguraPortraits() {
	}

	/**
	 * Resolves every member this feature uses.
	 *
	 * @return {@code null} on success, otherwise a description of the first member that is missing
	 */
	static String bind() {
		Class<?> manager = Reflection.findClass(AVATAR_MANAGER);
		if (manager == null) {
			return AVATAR_MANAGER;
		}

		Class<?> avatar = Reflection.findClass(AVATAR);
		if (avatar == null) {
			return AVATAR;
		}

		getAvatarForPlayer = Reflection.findMethod(manager, "getAvatarForPlayer", UUID.class);
		if (getAvatarForPlayer == null) {
			return AVATAR_MANAGER + "#getAvatarForPlayer(UUID)";
		}

		renderPortrait = Reflection.findMethod(avatar, "renderPortrait",
				GuiGraphicsExtractor.class, int.class, int.class, int.class, float.class, boolean.class);
		if (renderPortrait == null) {
			return AVATAR + "#renderPortrait(GuiGraphicsExtractor, int, int, int, float, boolean)";
		}

		return null;
	}

	/**
	 * The avatar for a player, fetching it from Figura's backend if it has not been seen yet.
	 *
	 * <p>Deliberately the fetching variant rather than {@code getLoadedAvatar}: this is what makes a
	 * head appear for someone whose avatar the client has not downloaded yet, and it is the call
	 * Figura's own tab-list face uses.
	 */
	static Object avatarFor(UUID player) {
		return Reflection.invoke(getAvatarForPlayer, null, player);
	}

	/**
	 * Submits the avatar's face.
	 *
	 * @return whether anything was drawn, i.e. whether the caller should skip its own face
	 */
	static boolean renderPortrait(Object avatar, GuiGraphicsExtractor graphics, int x, int y, int size,
			boolean upsideDown) {
		// Figura's tab list passes a model scale of twice the face size; matching it keeps a chat
		// head framed exactly like the tab-list face.
		return (Boolean) Reflection.invoke(renderPortrait, avatar, graphics, x, y, size, size * 2f, upsideDown);
	}
}
