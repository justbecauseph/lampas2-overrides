package lampas2overrides.client.figurareplay;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Moves the bridge's entries between {@code .mcpr} archives at the zip level.
 *
 * <p>Used only for ReplayMod's post-processing, which reads the original through ReplayStudio but
 * writes brand new output files and copies across just the metadata, markers, mod info and resource
 * packs. Everything else — including this bridge's avatars — is dropped, so they have to be carried
 * over afterwards. Plain zip filesystem access is enough and avoids reaching further into
 * ReplayStudio than the bridge already does.
 */
final class ReplayArchives {

	private ReplayArchives() {
	}

	/**
	 * Copies a replay's Figura entries into a temporary archive.
	 *
	 * <p>Post-processing moves the original out from under us before producing any output, so its
	 * entries have to be taken before it starts. A temp file rather than a byte array because a busy
	 * server's replay can carry an avatar per player.
	 *
	 * @return the temporary archive, or {@code null} if the replay has no Figura entries
	 */
	static Path stash(Path replay) throws IOException {
		try (FileSystem source = FileSystems.newFileSystem(replay, Map.<String, Object>of())) {
			Path root = source.getPath(AvatarIndex.DIRECTORY);
			if (!Files.isDirectory(root)) {
				return null;
			}

			Path stash = Files.createTempFile("lampas2-figura-replay", ".zip");
			Files.delete(stash); // The zip filesystem wants to create the file itself.

			try (FileSystem target = FileSystems.newFileSystem(stash, Map.of("create", "true"))) {
				copyTree(root, target);
			} catch (IOException e) {
				Files.deleteIfExists(stash);
				throw e;
			}

			return stash;
		}
	}

	/**
	 * Copies stashed entries into a replay that does not already have them.
	 *
	 * @return {@code true} if entries were added
	 */
	static boolean restore(Path stash, Path replay) throws IOException {
		try (FileSystem target = FileSystems.newFileSystem(replay, Map.<String, Object>of())) {
			if (Files.exists(target.getPath(AvatarIndex.INDEX_ENTRY))) {
				return false;
			}
			try (FileSystem source = FileSystems.newFileSystem(stash, Map.<String, Object>of())) {
				copyTree(source.getPath(AvatarIndex.DIRECTORY), target);
			}
			return true;
		}
	}

	private static void copyTree(Path sourceRoot, FileSystem target) throws IOException {
		try (Stream<Path> tree = Files.walk(sourceRoot)) {
			for (Path entry : (Iterable<Path>) tree::iterator) {
				// Zip filesystems cannot resolve each other's paths, so go via the entry name.
				Path destination = target.getPath(entry.toString());
				if (Files.isDirectory(entry)) {
					Files.createDirectories(destination);
					continue;
				}
				Path parent = destination.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
