package lampas2overrides.client.boatmask;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Exact, fail-closed policy and optional-EMF inspection for the boat water-mask shim.
 *
 * <p>EMF is intentionally resolved through reflection. It remains an optional client mod and is
 * never placed on this mod's compile or runtime class path.</p>
 */
public final class BoatWaterMaskCompatibility {

	public static final String EMF_ROOT_CLASS = "traben.entity_model_features.models.parts.EMFModelPartRoot";
	public static final String EMF_MOD_ID = BoatWaterMaskProfiles.EMF_MOD_ID;
	public static final String MASK_RESOURCE_PATH = "optifine/cem/boat_patch.jem";
	public static final String MASK_FINAL_FILE_LOCATION = "minecraft:optifine/cem/boat_patch.jem";
	public static final String EXPECTED_MASK_SHA256 =
		"4d924242a3787ca708b0165961078bcf91bd455d0bb881a10b9a28a0a3fa725b";
	public static final String EXPECTED_EMF_VERSION = BoatWaterMaskProfiles.EXPECTED_EMF_VERSION;
	public static final String EXPECTED_EMF_ARTIFACT_SHA256 = BoatWaterMaskProfiles.EXPECTED_EMF_ARTIFACT_SHA256;
	public static final String EXPECTED_PYRITE_ARTIFACT_SHA256 = BoatWaterMaskProfiles.EXPECTED_PYRITE_ARTIFACT_SHA256;

	private BoatWaterMaskCompatibility() {
	}

	public static boolean supportedEmfVersion(String version) {
		return BoatWaterMaskProfiles.supportedEmfVersion(version);
	}

	public static boolean matchesArtifact(net.fabricmc.loader.api.ModContainer container, String expectedSha256) {
		return BoatWaterMaskProfiles.matchesArtifact(container, expectedSha256);
	}

	public static boolean supportedProviderVersion(String modId, String version) {
		return BoatWaterMaskProfiles.supportedProviderVersion(modId, version);
	}

	public static boolean hasSupportedProvider(FabricLoader loader) {
		return BoatWaterMaskProfiles.hasSupportedProvider(loader);
	}

	public static boolean installedProviderMatches(FabricLoader loader, String modId) {
		return BoatWaterMaskProfiles.installedProviderMatches(loader, modId);
	}

	public static boolean isAuditedLayer(String modId, String version, ModelLayerLocation modelId) {
		return BoatWaterMaskProfiles.isAuditedLayer(
			modId, version, modelId.layer(), modelId.model().getNamespace(), modelId.model().getPath());
	}

	public static boolean requiredModsMatch(FabricLoader loader, String modId) {
		return BoatWaterMaskProfiles.requiredModsMatch(loader, modId);
	}

	/**
	 * Returns whether the constructor may replace the water patch for this renderer.
	 * Every input is explicit so tests can exercise each fail-closed gate without a running client.
	 */
	public static boolean shouldRepair(
		String modId,
		String providerVersion,
		ModelLayerLocation modelId,
		EmfRootInfo hullRoot,
		EmfRootInfo waterRoot,
		ResourceProof resourceProof
	) {
		return supportedEmfVersion(resourceProof.emfVersion())
			&& isAuditedLayer(modId, providerVersion, modelId)
			&& hullRoot.known()
			&& !hullRoot.hasCustomContent()
			&& waterRoot.known()
			&& waterRoot.emfRoot()
			&& waterRoot.containsCustomModel()
			&& waterRoot.containsCustomAnims()
			&& MASK_FINAL_FILE_LOCATION.equals(normalizePath(waterRoot.finalFileLocation()))
			&& resourceProof.resourcePresent()
			&& resourceProof.contentMatches();
	}

	/**
	 * Performs a fresh read of the selected resource. This method intentionally has no cache: a
	 * resource reload must be evaluated against the currently selected resource stack.
	 */
	public static ResourceProof inspectSelectedResource(ResourceManager resourceManager, String emfVersion) {
		if (resourceManager == null || !supportedEmfVersion(emfVersion)) {
			return ResourceProof.unavailable(emfVersion);
		}

		Optional<Resource> selected;
		try {
			selected = resourceManager.getResource(Identifier.fromNamespaceAndPath("minecraft", MASK_RESOURCE_PATH));
		} catch (RuntimeException exception) {
			return ResourceProof.unavailable(emfVersion);
		}
		if (selected.isEmpty()) {
			return ResourceProof.missing(emfVersion);
		}

		try (InputStream input = selected.get().open()) {
			return new ResourceProof(true, EXPECTED_MASK_SHA256.equals(sha256Hex(input)), emfVersion);
		} catch (IOException | RuntimeException exception) {
			return ResourceProof.unavailable(emfVersion);
		}
	}

	public static EmfRootInfo inspectEmfRoot(ModelPart part) {
		if (part == null) {
			return EmfRootInfo.unknown();
		}

		Class<?> emfRootClass;
		try {
			emfRootClass = Class.forName(EMF_ROOT_CLASS, false, part.getClass().getClassLoader());
		} catch (ClassNotFoundException exception) {
			return EmfRootInfo.vanilla();
		} catch (LinkageError exception) {
			return EmfRootInfo.unknown();
		}

		if (!emfRootClass.isInstance(part)) {
			return part.getClass() == ModelPart.class ? EmfRootInfo.vanilla() : EmfRootInfo.unknown();
		}

		try {
			Method getRoot = emfRootClass.getMethod("getRoot");
			Object root = getRoot.invoke(part);
			if (!emfRootClass.isInstance(root)) {
				return EmfRootInfo.unknown();
			}
			Field customModel = emfRootClass.getField("containsCustomModel");
			Field customAnims = emfRootClass.getField("containsCustomAnims");
			Field directoryContext = emfRootClass.getField("directoryContext");
			Object directory = directoryContext.get(root);
			String finalLocation = null;
			if (directory != null) {
				finalLocation = (String) directory.getClass().getMethod("getFinalFileLocation").invoke(directory);
			}
			return new EmfRootInfo(
				true,
				customModel.getBoolean(root),
				customAnims.getBoolean(root),
				finalLocation,
				true
			);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return EmfRootInfo.unknown();
		}
	}

	public static Model.Simple newVanillaWaterPatchModel() {
		return new Model.Simple(BoatModel.createWaterPatch().bakeRoot(), ignored -> RenderTypes.waterMask());
	}

	static String sha256Hex(InputStream input) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("SHA-256 is required by the Java runtime", exception);
		}
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) >= 0) {
			if (read > 0) {
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static String normalizePath(String path) {
		return path == null ? null : path.replace('\\', '/');
	}

	public record EmfRootInfo(
		boolean emfRoot,
		boolean containsCustomModel,
		boolean containsCustomAnims,
		String finalFileLocation,
		boolean known
	) {
		static EmfRootInfo vanilla() {
			return new EmfRootInfo(false, false, false, null, true);
		}

		static EmfRootInfo unknown() {
			return new EmfRootInfo(false, false, false, null, false);
		}

		boolean hasCustomContent() {
			return containsCustomModel || containsCustomAnims;
		}
	}

	public record ResourceProof(boolean resourcePresent, boolean contentMatches, String emfVersion) {
		static ResourceProof missing(String emfVersion) {
			return new ResourceProof(false, false, emfVersion);
		}

		static ResourceProof unavailable(String emfVersion) {
			return new ResourceProof(false, false, emfVersion);
		}
	}
}
