package lampas2overrides.client.voxyreversez;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * The audited Voxy and Iris artifacts. Voxy 0.2.19-beta forces forward-Z whenever a shader pack is on, which matched
 * Iris up to 1.11.2; Iris 1.11.4 keeps the game reverse-Z and rewrites pack shaders instead. The fix applies only to
 * this exact pair, because a newer Voxy release ships it (upstream branch {@code 263}) and an older Iris still undoes
 * reverse-Z itself.
 */
public final class VoxyReverseZProfiles {

	public static final ArtifactProfile VOXY =
		new ArtifactProfile("voxy", "0.2.19-beta", "a06e4d93db88dc22d6cde6dfe1e58d8428ae271d1f432bbb430735336f4b5c12");
	public static final ArtifactProfile IRIS =
		new ArtifactProfile("iris", "1.11.4+mc26.2", "f1f7ab57c974d193ba33aa285864a0ded949216f402116fceaf4dc7739b4dd7c");

	private VoxyReverseZProfiles() {
	}

	public enum Status {
		/** Voxy or Iris is not installed; the fix has nothing to do. */
		ABSENT,
		/** Both installed artifacts match their profiles. */
		SUPPORTED,
		/** Both are installed but at least one differs from its profile. */
		UNSUPPORTED
	}

	public static Status status(FabricLoader loader) {
		Optional<ModContainer> voxy = loader.getModContainer(VOXY.modId());
		Optional<ModContainer> iris = loader.getModContainer(IRIS.modId());
		if (voxy.isEmpty() || iris.isEmpty()) {
			return Status.ABSENT;
		}
		return VOXY.matches(voxy.get()) && IRIS.matches(iris.get()) ? Status.SUPPORTED : Status.UNSUPPORTED;
	}

	public static String describe(FabricLoader loader, ArtifactProfile profile) {
		return loader.getModContainer(profile.modId())
			.map(mod -> profile.modId() + " " + mod.getMetadata().getVersion().getFriendlyString())
			.orElse(profile.modId() + " (absent)");
	}

	public record ArtifactProfile(String modId, String version, String jarSha256) {

		public boolean matches(String modId, String version, String sha256) {
			return this.modId.equals(modId) && this.version.equals(version) && sha256 != null
				&& this.jarSha256.equalsIgnoreCase(sha256);
		}

		/** Checks the metadata and SHA-256 of the sole path that supplied the mod. */
		public boolean matches(ModContainer container) {
			try {
				String modId = container.getMetadata().getId();
				String version = container.getMetadata().getVersion().getFriendlyString();
				if (!this.modId.equals(modId) || !this.version.equals(version)) {
					return false;
				}
				return matches(modId, version, artifactSha256(container));
			} catch (RuntimeException exception) {
				return false;
			}
		}
	}

	private static String artifactSha256(ModContainer container) {
		ModOrigin origin = container.getOrigin();
		if (origin == null || origin.getKind() != ModOrigin.Kind.PATH) {
			return null;
		}
		List<Path> paths = origin.getPaths();
		if (paths == null || paths.size() != 1 || !Files.isRegularFile(paths.get(0))) {
			return null;
		}
		try (InputStream input = Files.newInputStream(paths.get(0))) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (IOException | NoSuchAlgorithmException exception) {
			return null;
		}
	}
}
