package lampas2overrides.client.figurareplay;

import lampas2overrides.client.compat.BridgeException;
import lampas2overrides.client.compat.Reflection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * Reads and writes arbitrary entries inside a {@code .mcpr} archive.
 *
 * <p>{@code ReplayFile#get} hands back ReplayStudio's shaded copy of Guava's {@code Optional}, so
 * its two methods are resolved from the declared return type. Resolving them from the returned
 * object instead would land on Guava's package-private {@code Present}/{@code Absent}, which
 * reflection cannot invoke.
 *
 * <p>Every access holds the archive's monitor: {@code ZipReplayFile} keeps its pending entries in
 * plain {@link java.util.HashMap}s, and ReplayMod's own save service takes the same lock.
 */
final class ReplayFiles {

	private static Method get;
	private static Method write;
	private static Method optionalIsPresent;
	private static Method optionalGet;

	private ReplayFiles() {
	}

	/** @return the entry's bytes, or {@code null} if the archive has no such entry */
	static byte[] read(Object replayFile, String entry) {
		bind(replayFile);
		synchronized (replayFile) {
			Object optional = Reflection.invoke(get, replayFile, entry);
			if (optional == null || !(Boolean) Reflection.invoke(optionalIsPresent, optional)) {
				return null;
			}
			try (InputStream in = (InputStream) Reflection.invoke(optionalGet, optional)) {
				return in.readAllBytes();
			} catch (IOException e) {
				throw new BridgeException("cannot read replay entry " + entry, e);
			}
		}
	}

	/**
	 * Queues an entry for the archive. ReplayMod persists it on its own {@code save()}, which for a
	 * recording happens once the connection closes.
	 */
	static void write(Object replayFile, String entry, byte[] data) {
		bind(replayFile);
		synchronized (replayFile) {
			try (OutputStream out = (OutputStream) Reflection.invoke(write, replayFile, entry)) {
				out.write(data);
			} catch (IOException e) {
				throw new BridgeException("cannot write replay entry " + entry, e);
			}
		}
	}

	private static synchronized void bind(Object replayFile) {
		if (get != null) {
			return;
		}

		Class<?> type = replayFile.getClass();
		Method getMethod = Reflection.findMethod(type, "get", String.class);
		Method writeMethod = Reflection.findMethod(type, "write", String.class);
		if (getMethod == null || writeMethod == null) {
			throw new BridgeException("ReplayFile is missing get(String)/write(String) on " + type.getName());
		}

		Class<?> optional = getMethod.getReturnType();
		Method isPresent = Reflection.findMethod(optional, "isPresent");
		Method unwrap = Reflection.findMethod(optional, "get");
		if (isPresent == null || unwrap == null) {
			throw new BridgeException("unexpected ReplayFile#get return type " + optional.getName());
		}

		optionalIsPresent = isPresent;
		optionalGet = unwrap;
		write = writeMethod;
		get = getMethod;
	}
}
