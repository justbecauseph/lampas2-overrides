package lampas2overrides.client.figurareplay;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * Feeds the avatars stored in a replay back to Figura while that replay is being watched.
 *
 * <p>Avatars are pushed in from here rather than pulled by Figura, because Figura's only entry
 * point for a player it has not seen is a backend fetch. That fetch is suppressed for any player
 * this replay has data for (see {@code AvatarManagerMixin}), which keeps the recorded avatar from
 * being overwritten by whatever the player is wearing now — and keeps replays working offline.
 */
final class AvatarPlayback {

	/** Shortest gap between re-applying the same avatar after something else cleared it. */
	private static final int REAPPLY_INTERVAL_TICKS = 100;

	/**
	 * Most pings replayed in one catch-up, bounding the pause after a seek.
	 *
	 * <p>Below this, the avatar's state after a seek is exactly what it was at that point in the
	 * recording. Above it the oldest pings are skipped, which for the usual
	 * {@code pings.setSomething(state)} shape is invisible — a later ping overwrites what a skipped
	 * one would have set — but can lose state for a ping that flips a value rather than setting it.
	 */
	private static final int MAX_PINGS_PER_CATCH_UP = 2000;

	/** How long ping replay waits for avatar scripts to load before giving up on waiting. */
	private static final int MAX_SCRIPT_WAIT_TICKS = 200;

	private Object replayHandler;
	private Object replayFile;
	private ExecutorService io;

	private List<AvatarIndex.Change> changes = List.of();
	private Set<UUID> recordedPlayers = Set.of();

	private List<PingLog.Record> pings = List.of();
	/** Index of the next ping to replay. */
	private int pingCursor;
	/** Replay time the pings have been brought up to, or {@link Integer#MIN_VALUE} after a rewind. */
	private int pingsAppliedThrough = Integer.MIN_VALUE;
	/** Consecutive ticks ping replay has been waiting on avatar scripts to load. */
	private int waitedForScripts;

	/** Hash currently handed to Figura, per player. */
	private final Map<UUID, String> applied = new HashMap<>();
	/** Tick at which each entry in {@link #applied} was handed over, to pace re-application. */
	private final Map<UUID, Integer> appliedAtTick = new HashMap<>();
	/** Hash currently being read out of the archive, per player. */
	private final Map<UUID, String> loading = new HashMap<>();
	/** Blobs the index references but the archive does not contain; never retried. */
	private final Set<String> missingBlobs = new HashSet<>();

	private final Queue<Loaded> loaded = new ConcurrentLinkedQueue<>();

	private record Loaded(UUID owner, String hash, CompoundTag nbt) {
	}

	/**
	 * Whether this replay carries an avatar for a player, and Figura should therefore not go to the
	 * backend for them.
	 *
	 * <p>Read from Figura's fetch path, which runs on the client thread, same as every mutation
	 * here.
	 */
	boolean hasRecordedAvatar(UUID player) {
		return recordedPlayers.contains(player);
	}

	void tick() {
		Object handler = ReplayModApi.currentReplayHandler();

		if (handler == null) {
			stop();
			return;
		}

		if (handler != replayHandler) {
			stop();
			start(handler);
		}

		applyLoadedBlobs();

		if (changes.isEmpty()) {
			return;
		}

		int time = ReplayModApi.playbackTimestamp(replayHandler);
		Map<UUID, String> desired = desiredAt(time);
		boolean queryable = FiguraApi.avatarsQueryable();

		for (UUID player : recordedPlayers) {
			String want = desired.get(player);
			String have = applied.get(player);

			if (want == null) {
				if (have != null) {
					FiguraApi.clearAvatars(player);
					applied.remove(player);
					appliedAtTick.remove(player);
					loading.remove(player);
				}
				continue;
			}

			if (want.equals(have) && !(queryable && wasClearedBehindOurBack(player))) {
				continue;
			}

			if (missingBlobs.contains(want) || want.equals(loading.get(player))) {
				continue;
			}

			loading.put(player, want);
			requestBlob(player, want);
		}

		replayPings(time);
	}

	/**
	 * Brings the avatars' script state up to a point in the replay.
	 *
	 * <p>Playing a ping is the only way to reproduce a toggled animation, and Lua state cannot be
	 * rewound — so seeking backwards means starting the scripts over and running the pings again
	 * from the beginning, capped so that the pause stays bounded.
	 */
	private void replayPings(int time) {
		if (pings.isEmpty()) {
			return;
		}

		if (time < pingsAppliedThrough) {
			restartScripts();
			return;
		}

		// A ping run against a script that has not finished loading is queued and then dropped, so
		// hold them back until it has — but not indefinitely, in case one never loads at all.
		if (!scriptsReady() && waitedForScripts++ < MAX_SCRIPT_WAIT_TICKS) {
			return;
		}
		waitedForScripts = 0;

		int end = pingCursor;
		while (end < pings.size() && pings.get(end).time() <= time) {
			end++;
		}

		int start = Math.max(pingCursor, end - MAX_PINGS_PER_CATCH_UP);
		if (start > pingCursor) {
			FiguraReplayBridge.LOGGER.debug("Skipping {} older ping(s) to keep the seek responsive",
					start - pingCursor);
		}

		for (int i = start; i < end; i++) {
			PingLog.Record ping = pings.get(i);
			Object avatar = FiguraApi.loadedAvatar(ping.owner());
			if (avatar != null) {
				FiguraApi.runPing(avatar, ping.pingId(), ping.payload());
			}
		}

		pingCursor = end;
		pingsAppliedThrough = time;
	}

	/** Drops every applied avatar so the main loop re-pushes it with a fresh Lua runtime. */
	private void restartScripts() {
		for (UUID player : applied.keySet()) {
			FiguraApi.clearAvatars(player);
		}
		applied.clear();
		appliedAtTick.clear();
		loading.clear();

		pingCursor = 0;
		pingsAppliedThrough = Integer.MIN_VALUE;
		waitedForScripts = 0;
	}

	/** Whether every avatar being shown has finished loading and can accept pings. */
	private boolean scriptsReady() {
		if (!loading.isEmpty()) {
			return false;
		}
		for (UUID player : applied.keySet()) {
			Object avatar = FiguraApi.loadedAvatar(player);
			if (avatar == null || !FiguraApi.isAvatarLoaded(avatar)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether something dropped an avatar this session handed to Figura.
	 *
	 * <p>Figura's "reload all avatars" button and {@code /figura reload} both wipe loaded avatars,
	 * and the bridge is the only thing that can put a replay's avatars back — nothing else knows
	 * they exist. Re-application is paced so that an avatar which refuses to load cannot turn into a
	 * per-tick reload loop.
	 */
	private boolean wasClearedBehindOurBack(UUID player) {
		if (FiguraApi.loadedAvatar(player) != null) {
			return false;
		}
		Integer since = appliedAtTick.get(player);
		return since == null || FiguraReplayBridge.clientTicks() - since >= REAPPLY_INTERVAL_TICKS;
	}

	/** Hands the replay's players back to Figura, so live avatars load again afterwards. */
	void stop() {
		if (replayHandler == null) {
			return;
		}

		for (UUID player : applied.keySet()) {
			try {
				FiguraApi.reloadAvatar(player);
			} catch (RuntimeException e) {
				FiguraReplayBridge.LOGGER.warn("Cannot restore the live avatar of {}", player, e);
			}
		}

		if (io != null) {
			io.shutdownNow();
		}

		replayHandler = null;
		replayFile = null;
		io = null;
		changes = List.of();
		recordedPlayers = Set.of();
		pings = List.of();
		pingCursor = 0;
		pingsAppliedThrough = Integer.MIN_VALUE;
		waitedForScripts = 0;
		applied.clear();
		appliedAtTick.clear();
		loading.clear();
		missingBlobs.clear();
		loaded.clear();

		FiguraReplayBridge.onReplayStopped();
	}

	private void start(Object handler) {
		replayHandler = handler;
		replayFile = ReplayModApi.replayFileOf(handler);

		FiguraReplayBridge.onReplayStarted();

		// Figura subscribes to every player in the tab list for live avatar updates. In a replay
		// those are the recorded players, whose live updates have nothing to do with what is being
		// watched, so drop the subscriptions we already hold. NetworkStuffMixin stops new ones.
		FiguraApi.unsubscribeAll();

		byte[] index = ReplayFiles.read(replayFile, AvatarIndex.INDEX_ENTRY);
		if (index == null) {
			// Recorded without this bridge. Figura falls back to the backend, as it does today.
			FiguraReplayBridge.LOGGER.debug("Replay carries no Figura avatar data");
			return;
		}

		changes = AvatarIndex.decode(index);

		Set<UUID> players = new HashSet<>();
		for (AvatarIndex.Change change : changes) {
			players.add(change.owner());
		}
		recordedPlayers = Set.copyOf(players);

		io = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "lampas2-figura-replay-loader");
			thread.setDaemon(true);
			return thread;
		});

		byte[] pingLog = ReplayFiles.read(replayFile, PingLog.ENTRY);
		pings = pingLog == null ? List.of() : PingLog.decode(pingLog);

		FiguraReplayBridge.LOGGER.info("Replay carries Figura avatars for {} player(s) and {} ping(s)",
				recordedPlayers.size(), pings.size());
	}

	/**
	 * Which avatar each player should be wearing at a point in the replay.
	 *
	 * <p>A player's <em>first</em> recorded avatar applies from the start of the replay regardless
	 * of its timestamp. That timestamp marks when the recording client finished downloading the
	 * avatar, not when the player put it on, so honouring it would leave players briefly skinned as
	 * vanilla for reasons that have nothing to do with what was recorded. Later changes are real
	 * changes and keep their timestamps.
	 */
	private Map<UUID, String> desiredAt(int time) {
		Map<UUID, String> desired = new HashMap<>();
		Set<UUID> seen = new HashSet<>();

		for (AvatarIndex.Change change : changes) {
			boolean first = seen.add(change.owner());
			if (first || change.time() <= time) {
				desired.put(change.owner(), change.hash());
			}
		}

		return desired;
	}

	private void requestBlob(UUID player, String hash) {
		Object archive = replayFile;
		ExecutorService executor = io;
		if (archive == null || executor == null) {
			return;
		}

		executor.execute(() -> {
			CompoundTag nbt = null;
			try {
				byte[] data = ReplayFiles.read(archive, AvatarIndex.blobEntry(hash));
				if (data != null) {
					nbt = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
				}
			} catch (Exception e) {
				FiguraReplayBridge.LOGGER.error("Cannot read avatar {} out of the replay", hash, e);
			}
			loaded.add(new Loaded(player, hash, nbt));
		});
	}

	private void applyLoadedBlobs() {
		Loaded result;
		while ((result = loaded.poll()) != null) {
			if (!result.hash().equals(loading.get(result.owner()))) {
				// Superseded while it was being read, e.g. the viewer scrubbed past another change.
				continue;
			}

			loading.remove(result.owner());

			if (result.nbt() == null) {
				missingBlobs.add(result.hash());
				FiguraReplayBridge.LOGGER.warn("Replay references avatar {} but does not contain it", result.hash());
				continue;
			}

			FiguraApi.setAvatar(result.owner(), result.nbt());
			applied.put(result.owner(), result.hash());
			appliedAtTick.put(result.owner(), FiguraReplayBridge.clientTicks());
		}
	}
}
