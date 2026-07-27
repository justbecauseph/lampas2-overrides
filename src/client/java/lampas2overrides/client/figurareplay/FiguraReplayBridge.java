package lampas2overrides.client.figurareplay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Makes Figura avatars survive into ReplayMod recordings, playback and video exports.
 *
 * <p>Three things stand between the two mods, and this bridge handles all three:
 *
 * <ol>
 *   <li><b>The data is not in the replay.</b> Avatars arrive over Figura's backend, not the
 *       Minecraft protocol, so nothing about them is recorded. {@link AvatarRecorder} stores them
 *       alongside the packet stream and {@link AvatarPlayback} serves them back.
 *   <li><b>Animation timing is wall-clock.</b> Figura advances Blockbench animations by real
 *       milliseconds. During an export that runs at anything other than real time — which is every
 *       export — animations would run at the wrong speed. {@link #animationMillis()} replaces the
 *       clock with the replay's own timeline while a replay is open.
 *   <li><b>Exports never call {@code Minecraft#runTick}.</b> That is where Figura applies and
 *       clears animation transforms, so exported footage would show avatars frozen mid-pose.
 *       {@link #beforeExportedFrame()} and {@link #afterExportedFrame()} drive it per frame
 *       instead.
 * </ol>
 *
 * <p>Everything is opt-out by absence: with either mod missing the bridge never registers, and a
 * replay recorded without it plays back exactly as it does today.
 */
public final class FiguraReplayBridge {

	static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/figura-replay");

	private static final String FIGURA = "figura";
	private static final String REPLAY_MOD = "replaymod";

	private static AvatarRecorder recorder;
	private static AvatarPlayback playback;
	private static boolean enabled;

	/** Client ticks since launch. Advances during exports too, where the game loop does not run. */
	private static int clientTicks;

	private static boolean replayActive;
	private static long replayAnchorMillis;
	private static double replayAnchorTicks;

	/** Avatar entries lifted out of a replay that ReplayMod is currently post-processing. */
	private static Path stashedAvatars;

	/**
	 * Keeps {@link #animationMillis()} continuous across the switch between the replay timeline and
	 * the wall clock. Without it, closing a replay hands Figura a multi-minute jump and animations
	 * that use it break for the rest of the session.
	 */
	private static long clockOffset;

	private FiguraReplayBridge() {
	}

	public static void init() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded(FIGURA) || !loader.isModLoaded(REPLAY_MOD)) {
			return;
		}

		String missing = FiguraApi.bind();
		if (missing == null) {
			missing = ReplayModApi.bind();
		}
		if (missing != null) {
			LOGGER.error("Figura/ReplayMod avatar bridge disabled: cannot resolve {}", missing);
			return;
		}

		recorder = new AvatarRecorder();
		playback = new AvatarPlayback();
		enabled = true;

		LOGGER.info("Figura/ReplayMod avatar bridge active");
	}

	/** Called from {@code MinecraftTickMixin} at the end of every client tick. */
	public static void onClientTick() {
		clientTicks++;

		if (!enabled) {
			return;
		}

		try {
			playback.tick();
			recorder.tick();
		} catch (Throwable t) {
			disable(t);
		}
	}

	// -- hooks called from the mixins -- //

	/** Whether a replay is open, in which case Figura should follow the replay's clock. */
	public static boolean isReplayActive() {
		return replayActive;
	}

	/** Whether the open replay carries this player's avatar, making a backend fetch pointless. */
	public static boolean hasRecordedAvatar(UUID player) {
		return enabled && playback.hasRecordedAvatar(player);
	}

	/**
	 * The clock Figura's animations run on.
	 *
	 * <p>While a replay is open this is derived from game ticks, so it tracks the replay's speed,
	 * stops when playback is paused, and advances one video frame at a time during an export. Only
	 * differences between successive readings matter to Figura, so anchoring the replay timeline to
	 * whatever the wall clock read at replay start keeps the two interchangeable.
	 */
	public static long animationMillis() {
		if (replayActive) {
			return replayAnchorMillis + Math.round((gameTime() - replayAnchorTicks) * 50.0);
		}
		return Util.getMillis() + clockOffset;
	}

	/**
	 * Records a ping the client just ran against an avatar.
	 *
	 * <p>Called from Figura's websocket thread as well as the client thread. Only meaningful while
	 * recording; during playback the bridge is the one running pings, and there is no recording
	 * session for them to land in.
	 */
	public static void onPing(Object avatar, int pingId, byte[] payload) {
		if (!enabled) {
			return;
		}
		try {
			recorder.recordPing(FiguraApi.avatarOwner(avatar), pingId, payload);
		} catch (Throwable t) {
			LOGGER.error("Cannot record a Figura ping", t);
		}
	}

	/** Ends the previous exported frame by clearing the animation transforms it applied. */
	public static void beforeExportedFrame() {
		if (!enabled) {
			return;
		}
		try {
			FiguraApi.clearAnimationsOnAll();
		} catch (Throwable t) {
			disable(t);
		}
	}

	/** Advances and applies animations for the frame that is about to be rendered. */
	public static void afterExportedFrame() {
		if (!enabled) {
			return;
		}
		try {
			FiguraApi.applyAnimationsToAll();
		} catch (Throwable t) {
			disable(t);
		}
	}

	/**
	 * Flushes pending avatar writes before ReplayMod saves the archive.
	 *
	 * <p>Called from the netty thread as the recorded connection closes.
	 */
	public static void onRecordingStopping(Object packetListener) {
		if (!enabled) {
			return;
		}
		try {
			recorder.stop(packetListener);
		} catch (Throwable t) {
			LOGGER.error("Cannot finish writing Figura avatars into the replay", t);
		}
	}

	/** Takes the avatar data out of a replay about to be post-processed, before it is moved away. */
	public static void beforePostProcessing(Path replay) {
		discardStash();
		try {
			stashedAvatars = ReplayArchives.stash(replay);
		} catch (Throwable t) {
			LOGGER.warn("Cannot preserve Figura avatars across post-processing of {}", replay, t);
		}
	}

	/**
	 * Puts the avatar data into whatever files post-processing produced.
	 *
	 * @param outputs ReplayMod's return value: pairs of output path and metadata
	 */
	public static void afterPostProcessing(Path replay, List<?> outputs) {
		Path stash = stashedAvatars;
		if (stash == null || outputs == null) {
			discardStash();
			return;
		}

		try {
			for (Object output : outputs) {
				Path target = pathOf(output);
				if (target == null) {
					continue;
				}
				// Do not test this against the input path: a single output keeps the input's name,
				// the input having been moved into the raw folder first. Whether the file already
				// has the entries is the honest question, and it also covers the no-work path,
				// where ReplayMod hands the untouched original straight back.
				if (ReplayArchives.restore(stash, target)) {
					LOGGER.info("Carried Figura avatars into post-processed replay {}", target.getFileName());
				}
			}
		} catch (Throwable t) {
			LOGGER.warn("Cannot carry Figura avatars into the post-processed replay", t);
		} finally {
			discardStash();
		}
	}

	/** The {@code Path} half of one of ReplayMod's output pairs, whose type it does not export. */
	private static Path pathOf(Object outputPair) {
		if (outputPair == null) {
			return null;
		}
		try {
			Object left = outputPair.getClass().getMethod("getLeft").invoke(outputPair);
			return left instanceof Path path ? path : null;
		} catch (Throwable t) {
			LOGGER.warn("Cannot read the output path from {}", outputPair.getClass(), t);
			return null;
		}
	}

	private static void discardStash() {
		Path stash = stashedAvatars;
		stashedAvatars = null;
		if (stash != null) {
			try {
				Files.deleteIfExists(stash);
			} catch (IOException e) {
				LOGGER.warn("Cannot delete temporary avatar stash {}", stash, e);
			}
		}
	}

	// -- clock bookkeeping, driven by AvatarPlayback -- //

	static void onReplayStarted() {
		if (replayActive) {
			return;
		}
		replayAnchorMillis = Util.getMillis() + clockOffset;
		replayAnchorTicks = gameTime();
		replayActive = true;
	}

	static void onReplayStopped() {
		if (!replayActive) {
			return;
		}
		long virtualNow = animationMillis();
		replayActive = false;
		clockOffset = virtualNow - Util.getMillis();
	}

	/** Client ticks since launch, as a coarse monotonic clock for the playback session. */
	static int clientTicks() {
		return clientTicks;
	}

	/** Ticks since launch including the current partial tick, i.e. game time in ticks. */
	private static double gameTime() {
		Minecraft client = Minecraft.getInstance();
		DeltaTracker delta = client == null ? null : client.getDeltaTracker();
		return clientTicks + (delta == null ? 0f : delta.getGameTimeDeltaPartialTick(false));
	}

	private static void disable(Throwable cause) {
		enabled = false;
		LOGGER.error("Disabling the Figura/ReplayMod avatar bridge after an unexpected failure", cause);
		try {
			playback.stop();
		} catch (Throwable t) {
			LOGGER.error("Cannot shut down replay avatar playback", t);
		}
	}
}
