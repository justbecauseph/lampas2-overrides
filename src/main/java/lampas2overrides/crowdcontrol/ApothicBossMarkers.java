package lampas2overrides.crowdcontrol;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the markers Apotheosis writes on its invaders and elites, through a reflective soft bridge to Placebo's
 * {@code PersistentData.peek(Entity)}, so Placebo and Apotheosis stay soft dependencies with no compile-time link.
 *
 * <p>Apotheosis keeps the markers in Placebo's per-entity {@code CompoundTag} (the Fabric attachment
 * {@code placebo:persistent_data}):
 * <ul>
 * <li>{@code Invader.initBoss} writes the boolean {@value #INVADER_KEY}. Apotheosis's own readers test it with
 * {@code contains}, so any value counts.</li>
 * <li>{@code Elite.initElite} writes the boolean {@value #ELITE_KEY} once the mob is a real elite. Before that, a pending
 * elite holds the elite id as a string under the same key and has no elite stats, so it does not count.</li>
 * </ul>
 *
 * <p>The method handle is resolved once, on first use. Without Placebo the bridge reports {@link State#MISSING} and marks
 * nothing. The first failure to resolve or call it, including Placebo being installed without that class, disables the
 * bridge for the session, logs one warning and reports {@link State#FAILED}. Only called on the server thread.
 */
public final class ApothicBossMarkers {
	private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/crowdcontrol");
	static final String PLACEBO_MOD_ID = "placebo";
	static final String API_CLASS = "dev.shadowsoffire.placebo.util.PersistentData";
	static final String METHOD = "peek";
	/** Mirrors Apotheosis {@code Invader.BOSS_KEY}. */
	public static final String INVADER_KEY = "apoth.boss";
	/** Mirrors Apotheosis {@code Elite.MINIBOSS_KEY}. */
	public static final String ELITE_KEY = "apoth.miniboss";

	/** Whether the Placebo lookup is usable. */
	public enum State {
		/** Placebo's {@code PersistentData.peek} is resolved and has not failed. */
		READY,
		/** Placebo is not installed, so no mob is marked. */
		MISSING,
		/** Resolving or calling the lookup failed; the bridge is off for this session. */
		FAILED
	}

	/** Which Apotheosis marker a mob carries. */
	public enum Marker {
		NONE,
		INVADER,
		ELITE
	}

	private static boolean initialised;
	private static State state = State.MISSING;
	private static MethodHandle peek;

	private ApothicBossMarkers() {
	}

	/** The marker in a Placebo data tag. A null tag, a pending elite and an elite flag of false give {@link Marker#NONE}. */
	public static Marker markerOf(@Nullable CompoundTag data) {
		if (data == null) {
			return Marker.NONE;
		}
		if (data.contains(INVADER_KEY)) {
			return Marker.INVADER;
		}
		// A pending elite stores a string here, which getBooleanOr reads as the default.
		if (data.getBooleanOr(ELITE_KEY, false)) {
			return Marker.ELITE;
		}
		return Marker.NONE;
	}

	/** The Apotheosis marker on the entity, or {@link Marker#NONE} when there is none or the bridge is unavailable. */
	public static Marker markerOf(Entity entity) {
		if (!initialised) {
			initialise();
		}
		if (state != State.READY) {
			return Marker.NONE;
		}
		try {
			return markerOf((CompoundTag) peek.invokeExact(entity));
		} catch (Throwable failure) {
			disable(failure);
			return Marker.NONE;
		}
	}

	/** Whether the bridge can read markers, resolving it first if no mob has been checked yet. */
	public static State state() {
		if (!initialised) {
			initialise();
		}
		return state;
	}

	private static void initialise() {
		initialised = true;
		Class<?> api;
		try {
			api = Class.forName(API_CLASS, false, ApothicBossMarkers.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError absent) {
			if (FabricLoader.getInstance().isModLoaded(PLACEBO_MOD_ID)) {
				// Placebo is installed but its API moved: protection is off, so say so.
				disable(absent);
			} else {
				state = State.MISSING;
			}
			return;
		}
		try {
			peek = MethodHandles.publicLookup().findStatic(api, METHOD,
					MethodType.methodType(CompoundTag.class, Entity.class));
			state = State.READY;
		} catch (Throwable failure) {
			disable(failure);
		}
	}

	private static void disable(Throwable failure) {
		if (state == State.FAILED) {
			return;
		}
		state = State.FAILED;
		LOGGER.warn(
				"Placebo marker lookup unavailable; Apotheosis elites and invaders are not protected ({})",
				failure.toString());
	}
}
