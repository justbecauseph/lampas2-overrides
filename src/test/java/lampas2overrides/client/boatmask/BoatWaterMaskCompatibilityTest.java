package lampas2overrides.client.boatmask;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

class BoatWaterMaskCompatibilityTest {

	private static final String EMF_VERSION = "3.3.5";
	private static final BoatWaterMaskCompatibility.EmfRootInfo VANILLA_HULL =
		new BoatWaterMaskCompatibility.EmfRootInfo(false, false, false, null, true);
	private static final BoatWaterMaskCompatibility.EmfRootInfo ACTIVE_WATER_ROOT =
		new BoatWaterMaskCompatibility.EmfRootInfo(
			true, true, true, BoatWaterMaskCompatibility.MASK_FINAL_FILE_LOCATION, true);
	private static final BoatWaterMaskCompatibility.ResourceProof ACTIVE_RESOURCE =
		new BoatWaterMaskCompatibility.ResourceProof(true, true, EMF_VERSION);

	@Test
	void acceptsEveryAuditedProviderLayer() {
		assertLayers("pyrite", "0.18.3+26.2", new String[] {
			"azalea", "black_stained", "blue_stained", "brown_mushroom", "brown_stained",
			"cyan_stained", "dragon_stained", "glow_stained", "gray_stained", "green_stained",
			"honey_stained", "light_blue_stained", "light_gray_stained", "lime_stained", "magenta_stained",
			"nostalgia_stained", "orange_stained", "pink_stained", "poisonous_stained", "purple_stained",
			"red_mushroom", "red_stained", "rose_stained", "star_stained", "white_stained", "yellow_stained"}, false);
		assertLayers("promenade", "5.6.0", new String[] {"sakura", "maple", "palm"}, false);
		assertLayers("wilderwild", "4.2.11-mc26.2", new String[] {"baobab", "willow", "cypress", "palm", "maple"}, false);
		assertLayers("betterend", "26.201.2", new String[] {
			"dragon_tree", "helix_tree", "jellyshroom", "lacugrove", "lucernia", "mossy_glowshroom",
			"pythadendron", "tenanea", "umbrella_tree"}, true);
		assertLayers("betternether", "26.201.2", new String[] {
			"anchor_tree", "crimson", "gloomwood", "mushroom_fir", "nether_mushroom", "nether_sakura",
			"rubeus", "stalagnate", "warped", "wart", "willow"}, true);
	}

	@Test
	void rejectsEntityIdStyleAndUnlistedLayers() {
		assertFalse(BoatWaterMaskCompatibility.isAuditedLayer(
			"pyrite", "0.18.3+26.2", layer("pyrite", "cyan_stained_boat")));
		assertFalse(BoatWaterMaskCompatibility.isAuditedLayer(
			"betterend", "26.201.2", layer("betterend", "boat/end_lotus_boat")));
		assertFalse(BoatWaterMaskCompatibility.isAuditedLayer(
			"betternether", "26.201.2", layer("betternether", "chest_boat/nether_reed_chest_boat")));
		assertFalse(BoatWaterMaskCompatibility.isAuditedLayer(
			"pyrite", "0.18.3+26.2", new ModelLayerLocation(Identifier.parse("pyrite:boat/cyan_stained"), "alt")));
		assertFalse(BoatWaterMaskCompatibility.isAuditedLayer(
			"unknown", "1.0.0", layer("unknown", "boat/example")));
	}

	@Test
	void acceptsOnlyPlainHullWithActiveEmfReplacement() {
		assertTrue(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			VANILLA_HULL, ACTIVE_WATER_ROOT, ACTIVE_RESOURCE));
		assertFalse(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			new BoatWaterMaskCompatibility.EmfRootInfo(true, true, false, null, true), ACTIVE_WATER_ROOT, ACTIVE_RESOURCE));
		assertFalse(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			new BoatWaterMaskCompatibility.EmfRootInfo(true, false, true, null, true), ACTIVE_WATER_ROOT, ACTIVE_RESOURCE));
		assertFalse(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			new BoatWaterMaskCompatibility.EmfRootInfo(false, false, false, null, false), ACTIVE_WATER_ROOT, ACTIVE_RESOURCE));
	}

	@Test
	void failsClosedForMissingMismatchAndUnknownProvenance() {
		BoatWaterMaskCompatibility.ResourceProof missing =
			new BoatWaterMaskCompatibility.ResourceProof(false, false, EMF_VERSION);
		BoatWaterMaskCompatibility.ResourceProof mismatch =
			new BoatWaterMaskCompatibility.ResourceProof(true, false, EMF_VERSION);
		BoatWaterMaskCompatibility.ResourceProof unknownVersion =
			new BoatWaterMaskCompatibility.ResourceProof(true, true, "3.3.6");
		assertFalse(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			VANILLA_HULL, ACTIVE_WATER_ROOT, missing));
		assertFalse(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			VANILLA_HULL, ACTIVE_WATER_ROOT, mismatch));
		assertFalse(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			VANILLA_HULL, ACTIVE_WATER_ROOT, unknownVersion));
		assertFalse(eligible("pyrite", "0.18.4+26.2", layer("pyrite", "boat/cyan_stained"),
			VANILLA_HULL, ACTIVE_WATER_ROOT, ACTIVE_RESOURCE));
		assertFalse(eligible("pyrite", "0.18.3+26.2", layer("pyrite", "boat/cyan_stained"),
			VANILLA_HULL,
			new BoatWaterMaskCompatibility.EmfRootInfo(true, true, true, "minecraft:emf/cem/boat_patch.jem", true),
			ACTIVE_RESOURCE));
	}

	@Test
	void hashesSelectedResourceBytesWithSha256() throws IOException {
		assertEquals(
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
			BoatWaterMaskCompatibility.sha256Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));
		assertNotEquals(BoatWaterMaskCompatibility.EXPECTED_MASK_SHA256,
			BoatWaterMaskCompatibility.sha256Hex(new ByteArrayInputStream("tampered".getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void hashesOnlyTheActualPathOriginArtifact(@TempDir Path tempDirectory) throws IOException {
		Path artifact = tempDirectory.resolve("entity_model_features.jar");
		Files.writeString(artifact, "abc", StandardCharsets.UTF_8);
		ModContainer container = pathOriginContainer(artifact, ModOrigin.Kind.PATH);

		assertTrue(BoatWaterMaskCompatibility.matchesArtifact(
			container, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
		assertFalse(BoatWaterMaskCompatibility.matchesArtifact(container, "tampered"));
		assertFalse(BoatWaterMaskCompatibility.matchesArtifact(
			pathOriginContainer(artifact, ModOrigin.Kind.UNKNOWN),
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
	}

	@Test
	void providerPresenceVersionAndCompanionGatesFailClosed() {
		FabricLoader promenadeLoader = loader(Map.of("promenade", metadataContainer("promenade", "5.6.0")));
		assertTrue(BoatWaterMaskProfiles.installedProviderMatches(promenadeLoader, "promenade"));
		assertTrue(BoatWaterMaskProfiles.hasSupportedProvider(promenadeLoader));
		assertFalse(BoatWaterMaskProfiles.installedProviderMatches(
			loader(Map.of("promenade", metadataContainer("promenade", "5.6.1"))), "promenade"));
		assertFalse(BoatWaterMaskProfiles.installedProviderMatches(loader(Map.of()), "promenade"));

		Map<String, ModContainer> betterEnd = Map.of(
			"betterend", metadataContainer("betterend", "26.201.2"),
			"wover-item", metadataContainer("wover-item", "26.201.2"));
		assertTrue(BoatWaterMaskProfiles.requiredModsMatch(loader(betterEnd), "betterend"));
		assertFalse(BoatWaterMaskProfiles.requiredModsMatch(loader(
			Map.of("betterend", metadataContainer("betterend", "26.201.2"))), "betterend"));
		assertFalse(BoatWaterMaskProfiles.requiredModsMatch(loader(Map.of(
			"betterend", metadataContainer("betterend", "26.201.2"),
			"wover-item", metadataContainer("wover-item", "26.201.1"))), "betterend"));
	}

	@Test
	void rebuiltWaterPatchMatchesVanillaGeometry() {
		Model.Simple rebuilt = BoatWaterMaskCompatibility.newVanillaWaterPatchModel();
		ModelPart expected = net.minecraft.client.model.object.boat.BoatModel.createWaterPatch().bakeRoot();
		assertGeometryEquals(expected, rebuilt.root());
	}

	private static boolean eligible(
		String modId,
		String version,
		ModelLayerLocation layer,
		BoatWaterMaskCompatibility.EmfRootInfo hull,
		BoatWaterMaskCompatibility.EmfRootInfo water,
		BoatWaterMaskCompatibility.ResourceProof resource
	) {
		return BoatWaterMaskCompatibility.shouldRepair(modId, version, layer, hull, water, resource);
	}

	private static void assertLayers(String modId, String version, String[] names, boolean fullItemSuffix) {
		for (String name : names) {
			String boatName = fullItemSuffix ? name + "_boat" : name;
			String chestName = fullItemSuffix ? name + "_chest_boat" : name;
			assertTrue(BoatWaterMaskCompatibility.isAuditedLayer(modId, version, layer(modId, "boat/" + boatName)),
				modId + " boat " + boatName);
			assertTrue(BoatWaterMaskCompatibility.isAuditedLayer(modId, version, layer(modId, "chest_boat/" + chestName)),
				modId + " chest boat " + chestName);
		}
	}

	private static ModelLayerLocation layer(String namespace, String path) {
		return new ModelLayerLocation(Identifier.fromNamespaceAndPath(namespace, path), "main");
	}

	private static ModContainer pathOriginContainer(Path path, ModOrigin.Kind kind) {
		ModOrigin origin = (ModOrigin) Proxy.newProxyInstance(
			ModOrigin.class.getClassLoader(),
			new Class<?>[] {ModOrigin.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getKind" -> kind;
				case "getPaths" -> List.of(path);
				default -> null;
			});
		return (ModContainer) Proxy.newProxyInstance(
			ModContainer.class.getClassLoader(),
			new Class<?>[] {ModContainer.class},
			(proxy, method, args) -> "getOrigin".equals(method.getName()) ? origin : null);
	}

	private static FabricLoader loader(Map<String, ModContainer> mods) {
		return proxy(FabricLoader.class, (proxy, method, args) -> {
			if ("getModContainer".equals(method.getName())) {
				return Optional.ofNullable(mods.get(args[0]));
			}
			return defaultValue(method.getReturnType());
		});
	}

	private static ModContainer metadataContainer(String id, String version) {
		ModMetadata metadata = proxy(ModMetadata.class, (proxy, method, args) -> {
			if ("getId".equals(method.getName())) {
				return id;
			}
			if ("getVersion".equals(method.getName())) {
				return Version.parse(version);
			}
			return defaultValue(method.getReturnType());
		});
		return proxy(ModContainer.class, (proxy, method, args) -> {
			if ("getMetadata".equals(method.getName())) {
				return metadata;
			}
			return defaultValue(method.getReturnType());
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == char.class) {
			return '\0';
		}
		return 0;
	}

	private static void assertGeometryEquals(ModelPart expected, ModelPart actual) {
		List<ModelPart> expectedParts = expected.getAllParts();
		List<ModelPart> actualParts = actual.getAllParts();
		assertEquals(expectedParts.size(), actualParts.size());
		for (int i = 0; i < expectedParts.size(); i++) {
			ModelPart expectedPart = expectedParts.get(i);
			ModelPart actualPart = actualParts.get(i);
			assertEquals(expectedPart.isEmpty(), actualPart.isEmpty(), "part " + i + " emptiness");
			assertEquals(expectedPart.x, actualPart.x, "part " + i + " x");
			assertEquals(expectedPart.y, actualPart.y, "part " + i + " y");
			assertEquals(expectedPart.z, actualPart.z, "part " + i + " z");
			assertEquals(expectedPart.xRot, actualPart.xRot, "part " + i + " xRot");
			assertEquals(expectedPart.yRot, actualPart.yRot, "part " + i + " yRot");
			assertEquals(expectedPart.zRot, actualPart.zRot, "part " + i + " zRot");
		}
		List<float[]> expectedExtents = extents(expected);
		List<float[]> actualExtents = extents(actual);
		assertEquals(expectedExtents.size(), actualExtents.size());
		for (int i = 0; i < expectedExtents.size(); i++) {
			assertArrayEquals(expectedExtents.get(i), actualExtents.get(i));
		}
	}

	private static List<float[]> extents(ModelPart part) {
		List<float[]> values = new ArrayList<>();
		com.mojang.blaze3d.vertex.PoseStack poseStack = new com.mojang.blaze3d.vertex.PoseStack();
		part.getExtentsForGui(poseStack, position -> values.add(new float[] {position.x(), position.y(), position.z()}));
		return values;
	}
}
