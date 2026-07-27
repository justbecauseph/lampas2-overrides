package lampas2overrides.client.figurareplay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The recorded stream of Figura pings, stored as {@code figura/pings.bin}.
 *
 * <p>Pings are how an avatar's script state reaches anyone but its owner — a toggled animation is
 * an owner-side keybind calling {@code pings.setWings(true)}, which the backend relays to every
 * viewer. They never touch the Minecraft protocol, so a replay that stores only the avatar plays it
 * back in its default state with every toggle off.
 *
 * <p>Binary rather than JSON because a ping payload is arbitrary bytes and there can be a great
 * many of them; base64 in JSON would cost roughly double for no benefit.
 */
final class PingLog {

	static final String ENTRY = "figura/pings.bin";

	private static final int FORMAT = 1;

	/**
	 * One ping as the recording client executed it.
	 *
	 * @param time    milliseconds into the recording
	 * @param owner   the avatar the ping was run against
	 * @param pingId  Figura's id for the ping function
	 * @param payload the ping's encoded arguments
	 */
	record Record(int time, UUID owner, int pingId, byte[] payload) {
	}

	private PingLog() {
	}

	static byte[] encode(Collection<Record> records) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(FORMAT);
			out.writeInt(records.size());
			for (Record record : records) {
				out.writeInt(record.time());
				out.writeLong(record.owner().getMostSignificantBits());
				out.writeLong(record.owner().getLeastSignificantBits());
				out.writeInt(record.pingId());
				out.writeInt(record.payload().length);
				out.write(record.payload());
			}
		}
		return bytes.toByteArray();
	}

	/** @return the recorded pings in time order, or an empty list if the log cannot be read */
	static List<Record> decode(byte[] data) {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			int format = in.readInt();
			if (format != FORMAT) {
				FiguraReplayBridge.LOGGER.warn(
						"Ignoring {}: it is format {} and this build reads format {}", ENTRY, format, FORMAT);
				return List.of();
			}

			int count = in.readInt();
			List<Record> records = new ArrayList<>(Math.min(count, 4096));
			for (int i = 0; i < count; i++) {
				int time = in.readInt();
				UUID owner = new UUID(in.readLong(), in.readLong());
				int pingId = in.readInt();
				byte[] payload = new byte[in.readInt()];
				in.readFully(payload);
				records.add(new Record(time, owner, pingId, payload));
			}
			return records;
		} catch (Exception e) {
			FiguraReplayBridge.LOGGER.warn("Ignoring malformed {}", ENTRY, e);
			return List.of();
		}
	}
}
