package lampas2overrides.client.frozenwind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Exact artifact gate for the FrozenLib/Wilder Wild wind lifecycle repair. */
public final class FrozenWindCompatibility {

	public static final String MINECRAFT_VERSION = "26.2";
	public static final String FROZENLIB_VERSION = "2.5.3-mc26.2";
	public static final String WILDERWILD_VERSION = "4.2.11-mc26.2";
	public static final String WIND_MANAGER_SHA256 =
		"a8845939d7dbfb3f9fa1d102fe77c1236c9d63b226e3b655dba9295c681c26d3";
	public static final String WILDERWILD_EXTENSION_SHA256 =
		"1e66dd8d15651c50dea4f1016f9de4ce614d2280e23d6babcc59df2d4df15d6b";
	public static final String ATTACHMENT_CHANGE_SHA256 =
		"f68661b4382a423b470cefe90967087bf96d6de701e6d120b3122133d9012d37";
	public static final String ATTACHMENT_API_MODULE_VERSION = "2.2.19+515ac5339e";

	private static final String WIND_MANAGER_CLASS = "net/frozenblock/lib/wind/WindManager.class";
	private static final String WILDERWILD_EXTENSION_CLASS =
		"net/frozenblock/wilderwild/wind/WWWindManagerExtension.class";
	private static final String ATTACHMENT_CHANGE_CLASS =
		"net/fabricmc/fabric/impl/attachment/sync/AttachmentChange.class";

	private FrozenWindCompatibility() {
	}

	public static GateResult evaluate(FabricLoader loader) {
		String minecraftVersion = loader.getModContainer("minecraft")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse(null);
		if (!exactVersion(minecraftVersion, MINECRAFT_VERSION)) {
			return GateResult.disabled("Minecraft metadata is " + minecraftVersion);
		}

		Optional<ModContainer> attachmentApi = loader.getModContainer("fabric-data-attachment-api-v1");
		if (attachmentApi.isEmpty()) {
			return GateResult.disabled("Fabric Data Attachment API is absent");
		}
		String attachmentVersion = attachmentApi.get().getMetadata().getVersion().getFriendlyString();
		if (!exactVersion(attachmentVersion, ATTACHMENT_API_MODULE_VERSION)) {
			return GateResult.disabled("Fabric Data Attachment API metadata is " + attachmentVersion);
		}

		Optional<ModContainer> frozenlib = loader.getModContainer("frozenlib");
		if (frozenlib.isEmpty()) {
			return GateResult.disabled("FrozenLib is absent");
		}
		String frozenVersion = frozenlib.get().getMetadata().getVersion().getFriendlyString();
		if (!exactVersion(frozenVersion, FROZENLIB_VERSION)) {
			return GateResult.disabled("FrozenLib metadata is " + frozenVersion);
		}

		Optional<ModContainer> wilderwild = loader.getModContainer("wilderwild");
		if (wilderwild.isEmpty()) {
			return GateResult.disabled("Wilder Wild is absent");
		}
		String wilderVersion = wilderwild.get().getMetadata().getVersion().getFriendlyString();
		if (!exactVersion(wilderVersion, WILDERWILD_VERSION)) {
			return GateResult.disabled("Wilder Wild metadata is " + wilderVersion);
		}

		try {
			String windHash = sha256(frozenlib.get().findPath(WIND_MANAGER_CLASS).orElse(null));
			if (!WIND_MANAGER_SHA256.equals(windHash)) {
				return GateResult.disabled("WindManager SHA-256 is " + windHash);
			}
			String extensionHash = sha256(wilderwild.get().findPath(WILDERWILD_EXTENSION_CLASS).orElse(null));
			if (!WILDERWILD_EXTENSION_SHA256.equals(extensionHash)) {
				return GateResult.disabled("Wilder Wild extension SHA-256 is " + extensionHash);
			}
			String attachmentHash = sha256(attachmentApi.get().findPath(ATTACHMENT_CHANGE_CLASS).orElse(null));
			if (!ATTACHMENT_CHANGE_SHA256.equals(attachmentHash)) {
				return GateResult.disabled("AttachmentChange SHA-256 is " + attachmentHash);
			}
		} catch (IOException exception) {
			return GateResult.disabled("Could not inspect the exact wind target classes: " + exception.getMessage());
		}

		return GateResult.supported();
	}

	static boolean exactVersion(String actual, String expected) {
		return expected != null && expected.equals(actual);
	}

	static String sha256(Path path) throws IOException {
		if (path == null || !Files.isRegularFile(path)) {
			return null;
		}
		try (InputStream input = Files.newInputStream(path)) {
			return sha256(input);
		}
	}

	static String sha256(InputStream input) throws IOException {
		if (input == null) {
			return null;
		}
		try (InputStream closeable = input) {
			try {
				return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(closeable.readAllBytes()));
			} catch (NoSuchAlgorithmException exception) {
				throw new AssertionError("SHA-256 is required by the Java runtime", exception);
			}
		}
	}

	public record GateResult(boolean enabled, String reason) {
		static GateResult supported() {
			return new GateResult(true, "exact target contracts matched");
		}

		static GateResult disabled(String reason) {
			return new GateResult(false, reason);
		}
	}
}
