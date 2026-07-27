package lampas2overrides.client.figurareplay;

import lampas2overrides.client.compat.BridgeException;
import lampas2overrides.client.compat.Reflection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * The slice of Figura the bridge talks to, resolved reflectively.
 *
 * <p>Avatars are handed around as bare {@link Object}s; the bridge only ever needs an avatar's
 * {@code nbt} and its animation entry points, so there is nothing to gain from a wrapper type.
 */
final class FiguraApi {

	private static final String AVATAR_MANAGER = "org.figuramc.figura.avatar.AvatarManager";
	private static final String AVATAR = "org.figuramc.figura.avatar.Avatar";
	private static final String NETWORK_STUFF = "org.figuramc.figura.backend2.NetworkStuff";

	private static Method getLoadedAvatar;
	private static Method setAvatar;
	private static Method clearAvatars;
	private static Method reloadAvatar;
	private static Method executeAll;
	private static Method unsubscribeAll;
	private static Method applyAnimations;
	private static Method clearAnimations;
	private static Method runPing;
	private static Field avatarNbt;
	private static Field avatarOwner;
	private static Field avatarLoaded;
	private static Field panic;

	private FiguraApi() {
	}

	/**
	 * Resolves every member the bridge uses.
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

		Class<?> network = Reflection.findClass(NETWORK_STUFF);
		if (network == null) {
			return NETWORK_STUFF;
		}

		getLoadedAvatar = Reflection.findMethod(manager, "getLoadedAvatar", UUID.class);
		if (getLoadedAvatar == null) {
			return AVATAR_MANAGER + "#getLoadedAvatar(UUID)";
		}

		setAvatar = Reflection.findMethod(manager, "setAvatar", UUID.class, CompoundTag.class);
		if (setAvatar == null) {
			return AVATAR_MANAGER + "#setAvatar(UUID, CompoundTag)";
		}

		clearAvatars = Reflection.findMethod(manager, "clearAvatars", UUID.class);
		if (clearAvatars == null) {
			return AVATAR_MANAGER + "#clearAvatars(UUID)";
		}

		reloadAvatar = Reflection.findMethod(manager, "reloadAvatar", UUID.class);
		if (reloadAvatar == null) {
			return AVATAR_MANAGER + "#reloadAvatar(UUID)";
		}

		// Erasure makes the declared Consumer<Avatar> a plain Consumer at the JVM level, so the
		// bridge can pass a Consumer<Object> and receive avatars untyped.
		executeAll = Reflection.findMethod(manager, "executeAll", String.class, Consumer.class);
		if (executeAll == null) {
			return AVATAR_MANAGER + "#executeAll(String, Consumer)";
		}

		unsubscribeAll = Reflection.findMethod(network, "unsubscribeAll");
		if (unsubscribeAll == null) {
			return NETWORK_STUFF + "#unsubscribeAll()";
		}

		applyAnimations = Reflection.findMethod(avatar, "applyAnimations");
		if (applyAnimations == null) {
			return AVATAR + "#applyAnimations()";
		}

		clearAnimations = Reflection.findMethod(avatar, "clearAnimations");
		if (clearAnimations == null) {
			return AVATAR + "#clearAnimations()";
		}

		runPing = Reflection.findMethod(avatar, "runPing", int.class, byte[].class);
		if (runPing == null) {
			return AVATAR + "#runPing(int, byte[])";
		}

		avatarNbt = Reflection.findField(avatar, "nbt");
		if (avatarNbt == null) {
			return AVATAR + "#nbt";
		}

		avatarOwner = Reflection.findField(avatar, "owner");
		if (avatarOwner == null) {
			return AVATAR + "#owner";
		}

		avatarLoaded = Reflection.findField(avatar, "loaded");
		if (avatarLoaded == null) {
			return AVATAR + "#loaded";
		}

		panic = Reflection.findField(manager, "panic");
		if (panic == null) {
			return AVATAR_MANAGER + "#panic";
		}

		return null;
	}

	/**
	 * Whether {@link #loadedAvatar} distinguishes "no avatar" from "not answering right now".
	 *
	 * <p>Figura returns {@code null} for every player while panic mode is on or between levels, and
	 * neither means anyone took their avatar off. Mirrors the guard inside {@code getLoadedAvatar}.
	 */
	static boolean avatarsQueryable() {
		return !(Boolean) Reflection.read(panic, null) && Minecraft.getInstance().level != null;
	}

	/** The avatar currently loaded for a player, without triggering a backend fetch. */
	static Object loadedAvatar(UUID player) {
		return Reflection.invoke(getLoadedAvatar, null, player);
	}

	/**
	 * The raw avatar data, or {@code null} while Figura is still parsing it.
	 *
	 * <p>Figura assigns this field from its own loader thread, so a {@code null} here means "not
	 * ready yet, ask again next tick" rather than "this player has no avatar".
	 */
	static CompoundTag avatarNbt(Object avatar) {
		return (CompoundTag) Reflection.read(avatarNbt, avatar);
	}

	static UUID avatarOwner(Object avatar) {
		return (UUID) Reflection.read(avatarOwner, avatar);
	}

	/**
	 * Whether the avatar's script is up and running.
	 *
	 * <p>{@code runPing} queues onto the avatar's event queue, and the queued call drops itself if
	 * the avatar is not loaded when it is drained — so replayed pings have to wait for this.
	 */
	static boolean isAvatarLoaded(Object avatar) {
		return (Boolean) Reflection.read(avatarLoaded, avatar);
	}

	/** Runs a ping against an avatar exactly as Figura's backend would. */
	static void runPing(Object avatar, int pingId, byte[] payload) {
		Reflection.invoke(runPing, avatar, pingId, payload);
	}

	static void setAvatar(UUID player, CompoundTag nbt) {
		Reflection.invoke(setAvatar, null, player, nbt);
	}

	static void clearAvatars(UUID player) {
		Reflection.invoke(clearAvatars, null, player);
	}

	/** Drops the bridge's avatar and lets Figura go back to the local file or the backend. */
	static void reloadAvatar(UUID player) {
		Reflection.invoke(reloadAvatar, null, player);
	}

	static void unsubscribeAll() {
		Reflection.invoke(unsubscribeAll, null);
	}

	static void applyAnimationsToAll() {
		forEachAvatar("replayApplyAnimations", avatar -> Reflection.invoke(applyAnimations, avatar));
	}

	static void clearAnimationsOnAll() {
		forEachAvatar("replayClearAnimations", avatar -> Reflection.invoke(clearAnimations, avatar));
	}

	private static void forEachAvatar(String profilerName, Consumer<Object> action) {
		Reflection.invoke(executeAll, null, profilerName, action);
	}
}
