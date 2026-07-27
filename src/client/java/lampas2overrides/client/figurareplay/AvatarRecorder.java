package lampas2overrides.client.figurareplay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * Copies the Figura avatars of everyone visible into the replay being recorded.
 *
 * <p>Figura avatars travel over Figura's own backend, never over the Minecraft protocol, so a
 * vanilla ReplayMod recording contains no trace of them. Without this, playing a replay back can
 * only re-fetch whatever those players happen to be wearing <em>today</em> — and nothing at all for
 * players who since changed avatar, went offline, or were never uploaded in the first place.
 *
 * <p>Detection is by avatar object identity: Figura constructs a fresh {@code Avatar} for every
 * load, so an unchanged reference means unchanged data and costs one map lookup per player per
 * tick. Hashing and archive writes happen on a background thread, since a large avatar takes long
 * enough to serialise to show up as a stutter.
 */
final class AvatarRecorder {

	/** How long to let queued archive writes finish once recording stops. */
	private static final long FLUSH_TIMEOUT_SECONDS = 15;

	/**
	 * How long a player must have no avatar before that counts as taking it off.
	 *
	 * <p>Figura briefly reports no avatar while it re-downloads one, and recording that gap
	 * faithfully would put a flicker of vanilla skin into the replay.
	 */
	private static final int REMOVAL_GRACE_TICKS = 40;

	/** How often the ping log is rewritten, so a crash mid-recording loses little. */
	private static final int PING_WRITE_INTERVAL_TICKS = 600;

	/**
	 * Ceiling on recorded pings, so a chatty avatar in a long session cannot grow without bound.
	 * Measured at a few dozen bytes per ping, which caps the log in the low megabytes.
	 */
	private static final int MAX_RECORDED_PINGS = 100_000;

	/** Avatar last captured per player, by identity. Client thread only. */
	private final Map<UUID, Object> lastSeenAvatar = new HashMap<>();

	/** Consecutive ticks a previously-seen player has had no avatar. Client thread only. */
	private final Map<UUID, Integer> missingTicks = new HashMap<>();

	private volatile Session session;

	/**
	 * The recording that has already been flushed.
	 *
	 * <p>ReplayMod keeps its packet listener reachable for a moment after the connection closes, and
	 * this makes sure a tick landing in that window does not start a second session on top of it.
	 */
	private volatile Object flushedListener;

	/** Client tick at which the ping log was last written out. */
	private int lastPingWriteTick;

	/**
	 * One recording's worth of state.
	 *
	 * <p>Everything below {@link #io} is touched from that executor alone, which is why a session is
	 * replaced wholesale rather than reset: a new recording gets fresh maps without having to reason
	 * about whether the previous thread has finished with the old ones.
	 */
	private static final class Session {
		final Object packetListener;
		final Object replayFile;
		final ExecutorService io;

		final Map<UUID, String> recordedHash = new HashMap<>();
		final Set<String> storedBlobs = new HashSet<>();
		final List<AvatarIndex.Change> changes = new ArrayList<>();

		/** Pings arrive on Figura's websocket thread as well as the client thread. */
		final Queue<PingLog.Record> pings = new ConcurrentLinkedQueue<>();
		final AtomicInteger pingCount = new AtomicInteger();
		volatile boolean pingsDirty;
		volatile boolean pingLimitReported;

		Session(Object packetListener, Object replayFile) {
			this.packetListener = packetListener;
			this.replayFile = replayFile;
			this.io = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "lampas2-figura-replay-recorder");
				thread.setDaemon(true);
				return thread;
			});
		}

		void submit(Runnable task) {
			try {
				io.execute(task);
			} catch (RejectedExecutionException ignored) {
				// Recording already stopped; whatever this was would not have made the file anyway.
			}
		}
	}

	void tick() {
		Object listener = ReplayModApi.currentPacketListener();
		Session current = session;

		if (listener == null || listener == flushedListener) {
			if (current != null) {
				stop(current.packetListener);
			}
			return;
		}

		if (current == null || listener != current.packetListener) {
			if (current != null) {
				stop(current.packetListener);
			}
			current = start(listener);
		}

		capture(current);
	}

	/**
	 * Waits for queued writes to land, so they are in the archive before ReplayMod saves it.
	 *
	 * <p>Called from the netty thread when the recorded connection closes, and from the client
	 * thread when a session ends any other way.
	 */
	synchronized void stop(Object listener) {
		Session current = session;
		if (listener == null || current == null || current.packetListener != listener) {
			return;
		}

		flushedListener = listener;
		session = null;
		lastSeenAvatar.clear();
		missingTicks.clear();

		current.submit(() -> {
			writePings(current);
			int recorded = current.recordedHash.size();
			if (recorded > 0) {
				FiguraReplayBridge.LOGGER.info("Stored {} Figura avatar(s) and {} ping(s) in the recording",
						recorded, current.pingCount.get());
			}
		});

		current.io.shutdown();
		try {
			if (!current.io.awaitTermination(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				FiguraReplayBridge.LOGGER.warn("Timed out writing Figura avatars into the replay");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private synchronized Session start(Object listener) {
		Session started = new Session(listener, ReplayModApi.replayFileBeingRecorded(listener));
		session = started;
		FiguraReplayBridge.LOGGER.debug("Recording Figura avatars into the active replay");
		return started;
	}

	/**
	 * Records a ping the client just ran, so playback can put the avatar back into the same state.
	 *
	 * <p>Called from Figura's websocket thread for other players' pings and from the client thread
	 * for the local player's own, hence the concurrent queue.
	 */
	void recordPing(UUID owner, int pingId, byte[] payload) {
		Session current = session;
		if (current == null) {
			return;
		}

		if (current.pingCount.get() >= MAX_RECORDED_PINGS) {
			if (!current.pingLimitReported) {
				current.pingLimitReported = true;
				FiguraReplayBridge.LOGGER.warn(
						"Recorded {} Figura pings; dropping the rest of this recording's pings", MAX_RECORDED_PINGS);
			}
			return;
		}

		int time = ReplayModApi.recordingTimestamp(current.packetListener);
		current.pings.add(new PingLog.Record(time, owner, pingId, payload));
		current.pingCount.incrementAndGet();
		current.pingsDirty = true;
	}

	private void capture(Session current) {
		maybeWritePings(current);

		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null || !FiguraApi.avatarsQueryable()) {
			return;
		}

		int time = ReplayModApi.recordingTimestamp(current.packetListener);

		for (UUID player : connection.getOnlinePlayerIds()) {
			Object avatar = FiguraApi.loadedAvatar(player);

			if (avatar == null) {
				if (lastSeenAvatar.containsKey(player)
						&& missingTicks.merge(player, 1, Integer::sum) >= REMOVAL_GRACE_TICKS) {
					lastSeenAvatar.remove(player);
					missingTicks.remove(player);
					submitRemoval(current, player, time);
				}
				continue;
			}

			missingTicks.remove(player);

			if (lastSeenAvatar.get(player) == avatar) {
				continue;
			}

			CompoundTag nbt = FiguraApi.avatarNbt(avatar);
			if (nbt == null) {
				// Figura is still parsing it. Leave lastSeenAvatar alone so the next tick retries.
				continue;
			}

			lastSeenAvatar.put(player, avatar);
			submitCapture(current, player, time, nbt);
		}
	}

	private void submitCapture(Session current, UUID player, int time, CompoundTag nbt) {
		current.submit(() -> {
			byte[] data;
			try {
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				NbtIo.writeCompressed(nbt, buffer);
				data = buffer.toByteArray();
			} catch (IOException e) {
				FiguraReplayBridge.LOGGER.error("Cannot serialise the avatar of {}", player, e);
				return;
			}

			String hash = sha1(data);
			if (hash.equals(current.recordedHash.get(player))) {
				// A reload that produced identical data; nothing changed as far as the replay cares.
				return;
			}

			if (current.storedBlobs.add(hash)) {
				ReplayFiles.write(current.replayFile, AvatarIndex.blobEntry(hash), data);
			}

			current.recordedHash.put(player, hash);
			current.changes.add(new AvatarIndex.Change(time, player, hash));
			writeIndex(current);
		});
	}

	private void submitRemoval(Session current, UUID player, int time) {
		current.submit(() -> {
			if (current.recordedHash.remove(player) == null) {
				return;
			}
			current.changes.add(new AvatarIndex.Change(time, player, null));
			writeIndex(current);
		});
	}

	private static void writeIndex(Session current) {
		ReplayFiles.write(current.replayFile, AvatarIndex.INDEX_ENTRY, AvatarIndex.encode(current.changes));
	}

	private void maybeWritePings(Session current) {
		int now = FiguraReplayBridge.clientTicks();
		if (!current.pingsDirty || now - lastPingWriteTick < PING_WRITE_INTERVAL_TICKS) {
			return;
		}
		lastPingWriteTick = now;
		current.pingsDirty = false;
		current.submit(() -> writePings(current));
	}

	/**
	 * Rewrites the whole ping log.
	 *
	 * <p>Replay entries have no append mode, so this is a full rewrite each time — cheap enough at
	 * a few dozen bytes per ping, and worth doing periodically so a crash costs at most one
	 * interval's worth.
	 */
	private static void writePings(Session current) {
		// Snapshot first: the queue is still being appended to from Figura's websocket thread, and
		// the encoded count has to match the records that follow it.
		List<PingLog.Record> snapshot = new ArrayList<>(current.pings);
		if (snapshot.isEmpty()) {
			return;
		}
		try {
			ReplayFiles.write(current.replayFile, PingLog.ENTRY, PingLog.encode(snapshot));
		} catch (IOException e) {
			FiguraReplayBridge.LOGGER.error("Cannot write the Figura ping log into the replay", e);
		}
	}

	private static String sha1(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new BridgeException("SHA-1 is unavailable", e);
		}
	}
}
