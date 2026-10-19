package lampas2overrides.datapack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 fingerprints of upstream mod resources, used to gate datapack replacements on exact content. */
public final class ResourceFingerprint {

	private ResourceFingerprint() {
	}

	public static boolean matches(Path resource, String expectedSha256) throws IOException {
		return expectedSha256.equals(sha256(resource));
	}

	public static String sha256(Path resource) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}

		try (InputStream input = Files.newInputStream(resource)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}

		return HexFormat.of().formatHex(digest.digest());
	}
}
