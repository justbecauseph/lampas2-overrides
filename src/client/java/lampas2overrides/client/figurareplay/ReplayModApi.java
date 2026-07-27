package lampas2overrides.client.figurareplay;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The slice of ReplayMod the bridge talks to, resolved reflectively.
 *
 * <p>Replay handlers, packet listeners and replay files are passed around as {@link Object}s. The
 * bridge never inspects them beyond the handful of members named here, and ReplayMod is not on the
 * compile classpath (its 26.2 build is produced by a preprocessor and is not published anywhere the
 * bridge could resolve it from).
 */
final class ReplayModApi {

	private static final String REPLAY_MODULE = "com.replaymod.replay.ReplayModReplay";
	private static final String REPLAY_HANDLER = "com.replaymod.replay.ReplayHandler";
	private static final String REPLAY_SENDER = "com.replaymod.replay.ReplaySender";
	private static final String RECORDING_MODULE = "com.replaymod.recording.ReplayModRecording";
	private static final String CONNECTION_HANDLER = "com.replaymod.recording.handler.ConnectionEventHandler";
	private static final String PACKET_LISTENER = "com.replaymod.recording.packet.PacketListener";

	private static Field replayModuleInstance;
	private static Method getReplayHandler;
	private static Method getReplayFile;
	private static Method getReplaySender;
	private static Method currentTimeStamp;

	private static Field recordingModuleInstance;
	private static Method getConnectionEventHandler;
	private static Method getPacketListener;
	private static Field packetListenerReplayFile;
	private static Method getCurrentDuration;

	private ReplayModApi() {
	}

	/**
	 * Resolves every member the bridge uses.
	 *
	 * @return {@code null} on success, otherwise a description of the first member that is missing
	 */
	static String bind() {
		Class<?> replayModule = Reflection.findClass(REPLAY_MODULE);
		if (replayModule == null) {
			return REPLAY_MODULE;
		}

		Class<?> replayHandler = Reflection.findClass(REPLAY_HANDLER);
		if (replayHandler == null) {
			return REPLAY_HANDLER;
		}

		Class<?> replaySender = Reflection.findClass(REPLAY_SENDER);
		if (replaySender == null) {
			return REPLAY_SENDER;
		}

		Class<?> recordingModule = Reflection.findClass(RECORDING_MODULE);
		if (recordingModule == null) {
			return RECORDING_MODULE;
		}

		Class<?> connectionHandler = Reflection.findClass(CONNECTION_HANDLER);
		if (connectionHandler == null) {
			return CONNECTION_HANDLER;
		}

		Class<?> packetListener = Reflection.findClass(PACKET_LISTENER);
		if (packetListener == null) {
			return PACKET_LISTENER;
		}

		replayModuleInstance = Reflection.findField(replayModule, "instance");
		if (replayModuleInstance == null) {
			return REPLAY_MODULE + "#instance";
		}

		getReplayHandler = Reflection.findMethod(replayModule, "getReplayHandler");
		if (getReplayHandler == null) {
			return REPLAY_MODULE + "#getReplayHandler()";
		}

		getReplayFile = Reflection.findMethod(replayHandler, "getReplayFile");
		if (getReplayFile == null) {
			return REPLAY_HANDLER + "#getReplayFile()";
		}

		getReplaySender = Reflection.findMethod(replayHandler, "getReplaySender");
		if (getReplaySender == null) {
			return REPLAY_HANDLER + "#getReplaySender()";
		}

		currentTimeStamp = Reflection.findMethod(replaySender, "currentTimeStamp");
		if (currentTimeStamp == null) {
			return REPLAY_SENDER + "#currentTimeStamp()";
		}

		recordingModuleInstance = Reflection.findField(recordingModule, "instance");
		if (recordingModuleInstance == null) {
			return RECORDING_MODULE + "#instance";
		}

		getConnectionEventHandler = Reflection.findMethod(recordingModule, "getConnectionEventHandler");
		if (getConnectionEventHandler == null) {
			return RECORDING_MODULE + "#getConnectionEventHandler()";
		}

		getPacketListener = Reflection.findMethod(connectionHandler, "getPacketListener");
		if (getPacketListener == null) {
			return CONNECTION_HANDLER + "#getPacketListener()";
		}

		packetListenerReplayFile = Reflection.findField(packetListener, "replayFile");
		if (packetListenerReplayFile == null) {
			return PACKET_LISTENER + "#replayFile";
		}

		getCurrentDuration = Reflection.findMethod(packetListener, "getCurrentDuration");
		if (getCurrentDuration == null) {
			return PACKET_LISTENER + "#getCurrentDuration()";
		}

		return null;
	}

	/** The handler for the replay being watched, or {@code null} when no replay is open. */
	static Object currentReplayHandler() {
		Object module = Reflection.read(replayModuleInstance, null);
		return module == null ? null : Reflection.invoke(getReplayHandler, module);
	}

	static Object replayFileOf(Object replayHandler) {
		return Reflection.invoke(getReplayFile, replayHandler);
	}

	/** Milliseconds into the replay that playback has reached. */
	static int playbackTimestamp(Object replayHandler) {
		Object sender = Reflection.invoke(getReplaySender, replayHandler);
		return (Integer) Reflection.invoke(currentTimeStamp, sender);
	}

	/** The listener for the recording in progress, or {@code null} when nothing is recording. */
	static Object currentPacketListener() {
		Object module = Reflection.read(recordingModuleInstance, null);
		if (module == null) {
			return null;
		}
		Object connection = Reflection.invoke(getConnectionEventHandler, module);
		return connection == null ? null : Reflection.invoke(getPacketListener, connection);
	}

	static Object replayFileBeingRecorded(Object packetListener) {
		return Reflection.read(packetListenerReplayFile, packetListener);
	}

	/** Milliseconds of recorded packet stream so far, i.e. the timestamp a change would land at. */
	static int recordingTimestamp(Object packetListener) {
		return (int) (long) (Long) Reflection.invoke(getCurrentDuration, packetListener);
	}
}
