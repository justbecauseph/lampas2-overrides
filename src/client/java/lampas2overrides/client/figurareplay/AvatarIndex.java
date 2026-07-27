package lampas2overrides.client.figurareplay;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;

/**
 * The bridge's on-disk format inside a {@code .mcpr}.
 *
 * <pre>
 *   figura/index.json          this index
 *   figura/avatars/&lt;hash&gt;.nbt  gzipped avatar data, one entry per distinct avatar
 * </pre>
 *
 * <p>The index is a list of changes rather than a flat player-to-avatar map so that a player who
 * swaps avatars mid-recording plays back with both. Blobs are keyed by content hash, so several
 * players wearing the same avatar cost one copy.
 */
final class AvatarIndex {

	static final String DIRECTORY = "figura";
	static final String INDEX_ENTRY = "figura/index.json";
	static final String BLOB_PREFIX = "figura/avatars/";
	static final int FORMAT = 1;

	private static final Gson GSON = new Gson();

	/**
	 * A player's avatar changing at a point in the recording.
	 *
	 * @param time  milliseconds into the recording
	 * @param owner the player
	 * @param hash  the avatar's content hash, or {@code null} once the player has no avatar
	 */
	record Change(int time, UUID owner, String hash) {
	}

	/** Wire shape of {@link #INDEX_ENTRY}. Field names are the serialised format; do not rename. */
	private static final class Wire {
		int format;
		List<WireChange> changes;
	}

	private static final class WireChange {
		int time;
		String uuid;
		String hash;
	}

	private AvatarIndex() {
	}

	static byte[] encode(List<Change> changes) {
		Wire wire = new Wire();
		wire.format = FORMAT;
		wire.changes = new ArrayList<>(changes.size());

		for (Change change : changes) {
			WireChange entry = new WireChange();
			entry.time = change.time();
			entry.uuid = change.owner().toString();
			entry.hash = change.hash();
			wire.changes.add(entry);
		}

		return GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * @return the recorded changes in time order, or an empty list if the index is absent,
	 *         malformed, or written by a format this build does not understand
	 */
	static List<Change> decode(byte[] data) {
		Wire wire;
		try {
			wire = GSON.fromJson(new String(data, StandardCharsets.UTF_8), Wire.class);
		} catch (Exception e) {
			FiguraReplayBridge.LOGGER.warn("Ignoring malformed {}", INDEX_ENTRY, e);
			return List.of();
		}

		if (wire == null || wire.changes == null) {
			return List.of();
		}

		if (wire.format != FORMAT) {
			FiguraReplayBridge.LOGGER.warn(
					"Ignoring {}: it is format {} and this build reads format {}",
					INDEX_ENTRY, wire.format, FORMAT);
			return List.of();
		}

		List<Change> changes = new ArrayList<>(wire.changes.size());
		for (WireChange entry : wire.changes) {
			if (entry == null || entry.uuid == null) {
				continue;
			}
			try {
				changes.add(new Change(entry.time, UUID.fromString(entry.uuid), entry.hash));
			} catch (IllegalArgumentException e) {
				FiguraReplayBridge.LOGGER.warn("Ignoring index entry with bad uuid {}", entry.uuid);
			}
		}

		// The recorder appends in time order, but a hand-edited or concatenated index might not be.
		changes.sort((a, b) -> Integer.compare(a.time(), b.time()));
		return changes;
	}

	static String blobEntry(String hash) {
		return BLOB_PREFIX + hash + ".nbt";
	}
}
