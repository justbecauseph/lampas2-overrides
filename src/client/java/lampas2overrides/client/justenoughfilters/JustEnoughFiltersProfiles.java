package lampas2overrides.client.justenoughfilters;

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
 * Artifact profile and activation gate for the Just Enough Filters + JEI R8 local-slot reuse compatibility fix.
 *
 * <p>Starting in JEI 30.47.0.239 ({@code mezz/JustEnoughItems@44bf26e}), JEI's release jar is shrunk with R8, which
 * reuses dead parameter local-variable slots inside {@code IngredientFilter#getIngredientListUncached} and
 * {@code IngredientListOverlayController#updateBounds}. Just Enough Filters 3.2.0 declares those target parameters in
 * its {@code @Inject} handlers even though the handlers never read them, so Mixin emits {@code ALOAD} instructions at
 * {@code RETURN}/{@code TAIL} that push the overwritten local slots and fail JVM bytecode verification with
 * {@link VerifyError}.
 *
 * <p>The runtime gate requires {@code jei} to be present (any version) and {@code justenoughfilters} to match the exact
 * audited 3.2.0 artifact.
 */
public final class JustEnoughFiltersProfiles {

	public static final String JEI_MOD_ID = "jei";
	public static final String INGREDIENT_FILTER_TARGET = "mezz.jei.gui.ingredients.IngredientFilter";
	public static final String OVERLAY_CONTROLLER_TARGET =
		"mezz.jei.gui.overlay.IngredientListOverlayController";

	public static final ArtifactProfile JUST_ENOUGH_FILTERS = new ArtifactProfile(
		"justenoughfilters",
		"3.2.0",
		"488ae982d99de57cb1505f63bcafb5d97d57e764988ac6e348209b8d0f7b0733"
	);

	public enum Status {
		ABSENT,
		SUPPORTED,
		UNSUPPORTED
	}

	private JustEnoughFiltersProfiles() {}

	public static Status status(FabricLoader loader) {
		if (loader == null) {
			return Status.ABSENT;
		}
		Optional<ModContainer> jef = loader.getModContainer(JUST_ENOUGH_FILTERS.modId());
		Optional<ModContainer> jei = loader.getModContainer(JEI_MOD_ID);
		if (jef.isEmpty() || jei.isEmpty()) {
			return Status.ABSENT;
		}
		if (JUST_ENOUGH_FILTERS.matches(jef.get())) {
			return Status.SUPPORTED;
		}
		return Status.UNSUPPORTED;
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
