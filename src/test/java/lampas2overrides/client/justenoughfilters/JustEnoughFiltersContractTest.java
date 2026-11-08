package lampas2overrides.client.justenoughfilters;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lampas2overrides.client.justenoughfilters.JustEnoughFiltersProfiles.Status;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

class JustEnoughFiltersContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.justenoughfilters.mixins.json";
	private static final String INSPECTED_JEI_SHA256 =
		"0f4adcbd1cfc69288d62cee490ce2a28a7794e28d694f21dbd24596c76a15a96";
	private static final String JEF_FILTER_MIXIN_ENTRY =
		"net/psunset/jef/mixin/JeiIngredientFilterMixin.class";
	private static final String JEF_CONTROLLER_MIXIN_ENTRY =
		"net/psunset/jef/mixin/JeiIngredientListOverlayControllerMixin.class";
	private static final String JEI_FILTER_ENTRY =
		"mezz/jei/gui/ingredients/IngredientFilter.class";
	private static final String JEI_CONTROLLER_ENTRY =
		"mezz/jei/gui/overlay/IngredientListOverlayController.class";

	@Test
	void registersTheFeatureAsClientOnlyAndRequired() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("JAVA_25", config.get("compatibilityLevel").getAsString());
		assertEquals("lampas2overrides.client.justenoughfilters.mixin", config.get("package").getAsString());
		assertEquals(
			"lampas2overrides.client.justenoughfilters.JustEnoughFiltersMixinPlugin",
			config.get("plugin").getAsString()
		);
		assertEquals(
			Set.of("IngredientFilterMixin", "IngredientListOverlayControllerMixin"),
			stringSet(config.getAsJsonArray("client"))
		);
		assertFalse(config.has("mixins"), "JustEnoughFilters mixins must be client-only");
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

		JsonObject metadata = readJson("fabric.mod.json");
		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			JsonObject obj = entry.getAsJsonObject();
			if (MIXIN_CONFIG.equals(obj.get("config").getAsString())) {
				registered = obj;
				break;
			}
		}
		assertNotNull(registered, "fabric.mod.json must register " + MIXIN_CONFIG);
		assertEquals("client", registered.get("environment").getAsString());
		JsonObject suggests = metadata.getAsJsonObject("suggests");
		assertEquals("3.2.0", suggests.get("justenoughfilters").getAsString());
		assertEquals("*", suggests.get("jei").getAsString());
		assertFalse(metadata.getAsJsonObject("depends").has("justenoughfilters"));
		assertFalse(metadata.getAsJsonObject("depends").has("jei"));
	}

	@Test
	void mixinClassesDeclarePseudoPriority1100AndUnremappedTargets() throws IOException {
		assertOurMixinClass(
			"lampas2overrides/client/justenoughfilters/mixin/IngredientFilterMixin.class",
			JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET
		);
		assertOurMixinClass(
			"lampas2overrides/client/justenoughfilters/mixin/IngredientListOverlayControllerMixin.class",
			JustEnoughFiltersProfiles.OVERLAY_CONTROLLER_TARGET
		);
	}

	@Test
	void profileGatesExactJustEnoughFiltersAndAnyInstalledJei() throws Exception {
		var jef = JustEnoughFiltersProfiles.JUST_ENOUGH_FILTERS;

		assertTrue(jef.matches("justenoughfilters", "3.2.0", jef.jarSha256().toUpperCase()));
		assertFalse(jef.matches("justenoughfilters", "3.2.1", jef.jarSha256()));
		assertFalse(jef.matches("justenoughfilters", "3.2.0", INSPECTED_JEI_SHA256));
		assertFalse(jef.matches("jei", "3.2.0", jef.jarSha256()));

		Path jefJar = findJarContaining(JEF_FILTER_MIXIN_ENTRY);
		Path jeiJar = findJarContaining(JEI_FILTER_ENTRY);
		ModContainer validJef = container("justenoughfilters", "3.2.0", origin(List.of(jefJar), ModOrigin.Kind.PATH));
		ModContainer validJei = container("jei", "30.48.0.244", origin(List.of(jeiJar), ModOrigin.Kind.PATH));
		ModContainer otherJei = container("jei", "30.48.0.241", origin(List.of(jefJar), ModOrigin.Kind.PATH));
		ModContainer wrongVersionJef = container("justenoughfilters", "3.2.1", origin(List.of(jefJar), ModOrigin.Kind.PATH));
		ModContainer wrongHashJef = container("justenoughfilters", "3.2.0", origin(List.of(jeiJar), ModOrigin.Kind.PATH));

		assertEquals(Status.ABSENT, JustEnoughFiltersProfiles.status(null));
		assertEquals(Status.ABSENT, JustEnoughFiltersProfiles.status(loader(Map.of())));
		assertEquals(Status.ABSENT, JustEnoughFiltersProfiles.status(loader(Map.of("justenoughfilters", validJef))));
		assertEquals(Status.ABSENT, JustEnoughFiltersProfiles.status(loader(Map.of("jei", validJei))));
		assertEquals(
			Status.SUPPORTED,
			JustEnoughFiltersProfiles.status(loader(Map.of("justenoughfilters", validJef, "jei", validJei)))
		);
		assertEquals(
			Status.SUPPORTED,
			JustEnoughFiltersProfiles.status(loader(Map.of("justenoughfilters", validJef, "jei", otherJei)))
		);
		assertEquals(
			Status.UNSUPPORTED,
			JustEnoughFiltersProfiles.status(loader(Map.of("justenoughfilters", wrongVersionJef, "jei", validJei)))
		);
		assertEquals(
			Status.UNSUPPORTED,
			JustEnoughFiltersProfiles.status(loader(Map.of("justenoughfilters", wrongHashJef, "jei", validJei)))
		);
	}

	@Test
	void pinnedUpstreamJarsMatchAuditedHashesAndContracts() throws Exception {
		Path jefJar = findJarContaining(JEF_FILTER_MIXIN_ENTRY);
		Path jeiJar = findJarContaining(JEI_FILTER_ENTRY);

		assertEquals(JustEnoughFiltersProfiles.JUST_ENOUGH_FILTERS.jarSha256(), sha256Hex(jefJar));
		assertEquals(INSPECTED_JEI_SHA256, sha256Hex(jeiJar));

		try (JarFile jef = new JarFile(jefJar.toFile())) {
			JsonObject jefMixins = readJsonFromJar(jef, "justenoughfilters.mixins.json");
			Set<String> jeiClientMixins = new HashSet<>();
			for (String mixin : stringSet(jefMixins.getAsJsonArray("client"))) {
				if (mixin.startsWith("Jei")) {
					jeiClientMixins.add(mixin);
				}
			}
			assertEquals(
				Set.of(
					"JeiIngredientFilterMixin",
					"JeiIngredientListOverlayControllerMixin",
					"JeiIngredientListOverlayLayoutMixin",
					"JeiIngredientListOverlayMixin"
				),
				jeiClientMixins
			);

			ClassNode filterMixin = readClassFromJar(jef, JEF_FILTER_MIXIN_ENTRY);
			MethodNode applyFilters = findMethod(
				filterMixin,
				"jef$applyFilters",
				JustEnoughFiltersBytecodePatcher.JEF_APPLY_FILTERS_DESC
			);
			assertNotNull(applyFilters);
			assertFalse(usesLocalSlot(applyFilters, 1), "jef$applyFilters must not read slot 1 (filterText)");

			ClassNode controllerMixin = readClassFromJar(jef, JEF_CONTROLLER_MIXIN_ENTRY);
			MethodNode updateJefBounds = findMethod(
				controllerMixin,
				"jef$updateJefBounds",
				JustEnoughFiltersBytecodePatcher.JEF_UPDATE_BOUNDS_DESC
			);
			assertNotNull(updateJefBounds);
			assertFalse(usesLocalSlot(updateJefBounds, 1), "jef$updateJefBounds must not read slot 1 (guiProperties)");
			assertFalse(
				usesLocalSlot(updateJefBounds, 2),
				"jef$updateJefBounds must not read slot 2 (guiExclusionAreas)"
			);
		}
	}

	@Test
	void reproducesJvmVerifyErrorOnUnpatchedWovenJeiClassesAndPassesAfterPatch() throws Exception {
		Path jefJar = findJarContaining(JEF_FILTER_MIXIN_ENTRY);
		Path jeiJar = findJarContaining(JEI_FILTER_ENTRY);

		try (JarFile jef = new JarFile(jefJar.toFile()); JarFile jei = new JarFile(jeiJar.toFile())) {
			// 1. IngredientFilter#getIngredientListUncached
			ClassNode unpatchedFilter = weaveIngredientFilter(
				readClassFromJar(jei, JEI_FILTER_ENTRY),
				readClassFromJar(jef, JEF_FILTER_MIXIN_ENTRY)
			);
			byte[] unpatchedFilterBytes = writeClass(unpatchedFilter, jeiJar, jefJar);
			VerifyError filterVerifyError = assertThrows(
				VerifyError.class,
				() -> verifyInJvm(
					JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
					unpatchedFilterBytes,
					jeiJar,
					jefJar
				)
			);
			assertTrue(
				filterVerifyError.getMessage().contains("java/util/List")
					&& filterVerifyError.getMessage().contains("java/lang/String"),
				filterVerifyError.getMessage()
			);

			JustEnoughFiltersBytecodePatcher.patch(
				JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
				unpatchedFilter
			);
			byte[] patchedFilterBytes = writeClass(unpatchedFilter, jeiJar, jefJar);
			assertDoesNotThrow(() -> verifyInJvm(
				JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
				patchedFilterBytes,
				jeiJar,
				jefJar
			));

			// 2. IngredientListOverlayController#updateBounds
			// R8 overwrites slot 1 (IGuiProperties) with boolean (istore_1), Layout (astore_1), and
			// SearchAndConfigAreas (astore_1). Because IGuiProperties is an interface, JVMS 4.10.1.2 allows any
			// reference type at verification time (passing SearchAndConfigAreas as IGuiProperties), and would fail
			// with VerifyError if slot 1 still held the boolean from istore_1.
			// Because jef$updateJefBounds is non-cancellable and does not read its CallbackInfo parameter, Mixin's
			// CallbackInjector#loadOrCreateCallbackInfo emits ACONST_NULL for the CallbackInfo argument.
			ClassNode wovenController = weaveOverlayController(
				readClassFromJar(jei, JEI_CONTROLLER_ENTRY),
				readClassFromJar(jef, JEF_CONTROLLER_MIXIN_ENTRY),
				false
			);
			MethodNode updateBounds = findMethod(
				wovenController,
				JustEnoughFiltersBytecodePatcher.UPDATE_BOUNDS_NAME,
				JustEnoughFiltersBytecodePatcher.UPDATE_BOUNDS_DESC
			);
			assertTrue(overwritesSlotWithIntAndObject(updateBounds, 1));

			JustEnoughFiltersBytecodePatcher.patch(
				JustEnoughFiltersProfiles.OVERLAY_CONTROLLER_TARGET,
				wovenController
			);
			byte[] patchedControllerBytes = writeClass(wovenController, jeiJar, jefJar);
			assertDoesNotThrow(() -> verifyInJvm(
				JustEnoughFiltersProfiles.OVERLAY_CONTROLLER_TARGET,
				patchedControllerBytes,
				jeiJar,
				jefJar
			));

			// Also verify the inline NEW CallbackInfo(...) sequence when usesCallbackInfo is true.
			ClassNode wovenControllerInlineCi = weaveOverlayController(
				readClassFromJar(jei, JEI_CONTROLLER_ENTRY),
				readClassFromJar(jef, JEF_CONTROLLER_MIXIN_ENTRY),
				true
			);
			JustEnoughFiltersBytecodePatcher.patch(
				JustEnoughFiltersProfiles.OVERLAY_CONTROLLER_TARGET,
				wovenControllerInlineCi
			);
			byte[] patchedControllerInlineCiBytes = writeClass(wovenControllerInlineCi, jeiJar, jefJar);
			assertDoesNotThrow(() -> verifyInJvm(
				JustEnoughFiltersProfiles.OVERLAY_CONTROLLER_TARGET,
				patchedControllerInlineCiBytes,
				jeiJar,
				jefJar
			));
		}
	}

	@Test
	void failsLoudlyWhenTargetMethodCallSiteOrInstructionPatternChanges() throws Exception {
		Path jefJar = findJarContaining(JEF_FILTER_MIXIN_ENTRY);
		Path jeiJar = findJarContaining(JEI_FILTER_ENTRY);

		try (JarFile jef = new JarFile(jefJar.toFile()); JarFile jei = new JarFile(jeiJar.toFile())) {
			ClassNode rawFilter = readClassFromJar(jei, JEI_FILTER_ENTRY);
			assertThrows(
				IllegalStateException.class,
				() -> JustEnoughFiltersBytecodePatcher.patch("unknown.Target", rawFilter)
			);

			// Unwoven target class has no handler$*$jef$applyFilters call site.
			assertThrows(
				IllegalStateException.class,
				() -> JustEnoughFiltersBytecodePatcher.patch(
					JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
					rawFilter
				)
			);

			// If the handler ever reads slot 1, patching must refuse loudly.
			ClassNode filterWithUsedParam = weaveIngredientFilter(
				readClassFromJar(jei, JEI_FILTER_ENTRY),
				readClassFromJar(jef, JEF_FILTER_MIXIN_ENTRY)
			);
			MethodNode mergedHandler = findMethod(
				filterWithUsedParam,
				"handler$zef000$justenoughfilters$jef$applyFilters",
				JustEnoughFiltersBytecodePatcher.JEF_APPLY_FILTERS_DESC
			);
			mergedHandler.instructions.insert(new InsnNode(Opcodes.POP));
			mergedHandler.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 1));
			assertThrows(
				IllegalStateException.class,
				() -> JustEnoughFiltersBytecodePatcher.patch(
					JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
					filterWithUsedParam
				)
			);

			// Applying the patch a second time fails because ALOAD 1 is already ACONST_NULL.
			ClassNode patchedOnce = weaveIngredientFilter(
				readClassFromJar(jei, JEI_FILTER_ENTRY),
				readClassFromJar(jef, JEF_FILTER_MIXIN_ENTRY)
			);
			JustEnoughFiltersBytecodePatcher.patch(
				JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
				patchedOnce
			);
			assertThrows(
				IllegalStateException.class,
				() -> JustEnoughFiltersBytecodePatcher.patch(
					JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
					patchedOnce
				)
			);
		}
	}

	private static void assertOurMixinClass(String resourceName, String expectedTarget) throws IOException {
		try (InputStream input = JustEnoughFiltersContractTest.class.getClassLoader().getResourceAsStream(resourceName)) {
			assertNotNull(input, resourceName);
			ClassNode node = new ClassNode();
			new ClassReader(input).accept(node, 0);

			boolean hasPseudo = false;
			AnnotationNode mixinAnnotation = null;
			if (node.invisibleAnnotations != null) {
				for (AnnotationNode ann : node.invisibleAnnotations) {
					if ("Lorg/spongepowered/asm/mixin/Pseudo;".equals(ann.desc)) {
						hasPseudo = true;
					} else if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(ann.desc)) {
						mixinAnnotation = ann;
					}
				}
			}
			assertTrue(hasPseudo, resourceName + " must be @Pseudo");
			assertNotNull(mixinAnnotation, resourceName + " must have @Mixin");

			Map<String, Object> values = annotationValues(mixinAnnotation);
			assertEquals(List.of(expectedTarget), values.get("targets"));
			assertEquals(1100, values.get("priority"));
			assertEquals(Boolean.FALSE, values.get("remap"));
		}
	}

	/**
	 * Simulates SpongePowered Mixin's {@code CallbackInjector} weaving {@code JeiIngredientFilterMixin#jef$applyFilters}
	 * ({@code @At("RETURN"), cancellable = true}) into {@code IngredientFilter#getIngredientListUncached}, matching the
	 * exact bytecode and stackmap frame layout from the client crash log.
	 */
	private static ClassNode weaveIngredientFilter(ClassNode targetClass, ClassNode mixinClass) {
		String handlerName = "handler$zef000$justenoughfilters$jef$applyFilters";
		MethodNode mixinHandler = findMethod(
			mixinClass,
			"jef$applyFilters",
			JustEnoughFiltersBytecodePatcher.JEF_APPLY_FILTERS_DESC
		);
		targetClass.methods.add(cloneMergedHandler(mixinHandler, mixinClass.name, targetClass.name, handlerName));

		MethodNode targetMethod = findMethod(
			targetClass,
			JustEnoughFiltersBytecodePatcher.GET_INGREDIENT_LIST_UNCACHED_NAME,
			JustEnoughFiltersBytecodePatcher.GET_INGREDIENT_LIST_UNCACHED_DESC
		);
		AbstractInsnNode areturn = findSingleOpcode(targetMethod, Opcodes.ARETURN);

		int cirSlot = 2;
		LabelNode notCancelled = new LabelNode();
		InsnList injected = new InsnList();
		// CallbackInjector.dupReturnValue + createCallbackInfo + invokeCallback + injectCancellationCode
		injected.add(new InsnNode(Opcodes.DUP));
		injected.add(new VarInsnNode(Opcodes.ASTORE, cirSlot));
		injected.add(new TypeInsnNode(
			Opcodes.NEW,
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"
		));
		injected.add(new InsnNode(Opcodes.DUP));
		injected.add(new LdcInsnNode("getIngredientListUncached"));
		injected.add(new InsnNode(Opcodes.ICONST_1));
		injected.add(new VarInsnNode(Opcodes.ALOAD, cirSlot));
		injected.add(new MethodInsnNode(
			Opcodes.INVOKESPECIAL,
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
			"<init>",
			"(Ljava/lang/String;ZLjava/lang/Object;)V",
			false
		));
		injected.add(new VarInsnNode(Opcodes.ASTORE, cirSlot));
		injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
		injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
		injected.add(new VarInsnNode(Opcodes.ALOAD, cirSlot));
		injected.add(new MethodInsnNode(
			Opcodes.INVOKEVIRTUAL,
			targetClass.name,
			handlerName,
			JustEnoughFiltersBytecodePatcher.JEF_APPLY_FILTERS_DESC,
			false
		));
		injected.add(new VarInsnNode(Opcodes.ALOAD, cirSlot));
		injected.add(new MethodInsnNode(
			Opcodes.INVOKEVIRTUAL,
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
			"isCancelled",
			"()Z",
			false
		));
		injected.add(new JumpInsnNode(Opcodes.IFEQ, notCancelled));
		injected.add(new VarInsnNode(Opcodes.ALOAD, cirSlot));
		injected.add(new MethodInsnNode(
			Opcodes.INVOKEVIRTUAL,
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
			"getReturnValue",
			"()Ljava/lang/Object;",
			false
		));
		injected.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/stream/Stream"));
		injected.add(new InsnNode(Opcodes.ARETURN));
		injected.add(notCancelled);
		injected.add(new FrameNode(
			Opcodes.F_FULL,
			3,
			new Object[] {
				targetClass.name,
				"java/util/List",
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"
			},
			1,
			new Object[] {"java/util/stream/Stream"}
		));

		targetMethod.instructions.insertBefore(areturn, injected);
		targetMethod.maxStack = Math.max(targetMethod.maxStack, 6);
		targetMethod.maxLocals = Math.max(targetMethod.maxLocals, 3);
		return targetClass;
	}

	/**
	 * Simulates SpongePowered Mixin's {@code CallbackInjector} weaving
	 * {@code JeiIngredientListOverlayControllerMixin#jef$updateJefBounds} ({@code @At("TAIL"), cancellable = false})
	 * into {@code IngredientListOverlayController#updateBounds}.
	 */
	private static ClassNode weaveOverlayController(
		ClassNode targetClass,
		ClassNode mixinClass,
		boolean usesCallbackInfo
	) {
		String handlerName = "handler$zef000$justenoughfilters$jef$updateJefBounds";
		MethodNode mixinHandler = findMethod(
			mixinClass,
			"jef$updateJefBounds",
			JustEnoughFiltersBytecodePatcher.JEF_UPDATE_BOUNDS_DESC
		);
		targetClass.methods.add(cloneMergedHandler(mixinHandler, mixinClass.name, targetClass.name, handlerName));

		MethodNode targetMethod = findMethod(
			targetClass,
			JustEnoughFiltersBytecodePatcher.UPDATE_BOUNDS_NAME,
			JustEnoughFiltersBytecodePatcher.UPDATE_BOUNDS_DESC
		);
		AbstractInsnNode vreturn = findSingleOpcode(targetMethod, Opcodes.RETURN);

		InsnList injected = new InsnList();
		injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
		injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
		injected.add(new VarInsnNode(Opcodes.ALOAD, 2));
		if (usesCallbackInfo) {
			injected.add(new TypeInsnNode(
				Opcodes.NEW,
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfo"
			));
			injected.add(new InsnNode(Opcodes.DUP));
			injected.add(new LdcInsnNode("updateBounds"));
			injected.add(new InsnNode(Opcodes.ICONST_0));
			injected.add(new MethodInsnNode(
				Opcodes.INVOKESPECIAL,
				"org/spongepowered/asm/mixin/injection/callback/CallbackInfo",
				"<init>",
				"(Ljava/lang/String;Z)V",
				false
			));
		} else {
			injected.add(new InsnNode(Opcodes.ACONST_NULL));
		}
		injected.add(new MethodInsnNode(
			Opcodes.INVOKEVIRTUAL,
			targetClass.name,
			handlerName,
			JustEnoughFiltersBytecodePatcher.JEF_UPDATE_BOUNDS_DESC,
			false
		));

		targetMethod.instructions.insertBefore(vreturn, injected);
		targetMethod.maxStack = Math.max(targetMethod.maxStack, 11);
		return targetClass;
	}

	private static MethodNode cloneMergedHandler(
		MethodNode source,
		String mixinInternalName,
		String targetInternalName,
		String mergedName
	) {
		MethodNode copy = new MethodNode(
			Opcodes.ASM9,
			source.access,
			mergedName,
			source.desc,
			source.signature,
			source.exceptions == null ? null : source.exceptions.toArray(String[]::new)
		);
		source.accept(copy);
		for (AbstractInsnNode insn = copy.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode fieldInsn && mixinInternalName.equals(fieldInsn.owner)) {
				fieldInsn.owner = targetInternalName;
			} else if (insn instanceof MethodInsnNode methodInsn && mixinInternalName.equals(methodInsn.owner)) {
				methodInsn.owner = targetInternalName;
			}
		}
		return copy;
	}

	private static byte[] writeClass(ClassNode classNode, Path jeiJar, Path jefJar) throws Exception {
		URL[] urls = new URL[] {jeiJar.toUri().toURL(), jefJar.toUri().toURL()};
		try (URLClassLoader hierarchyLoader = new URLClassLoader(
			urls,
			JustEnoughFiltersContractTest.class.getClassLoader()
		)) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
				@Override
				protected ClassLoader getClassLoader() {
					return hierarchyLoader;
				}
			};
			classNode.accept(writer);
			return writer.toByteArray();
		}
	}

	private static void verifyInJvm(String binaryName, byte[] classBytes, Path jeiJar, Path jefJar) throws Exception {
		URL[] urls = new URL[] {jeiJar.toUri().toURL(), jefJar.toUri().toURL()};
		try (VerifyingClassLoader loader = new VerifyingClassLoader(binaryName, classBytes, urls, jeiJar)) {
			Class<?> loaded = Class.forName(binaryName, false, loader);
			// getDeclaredMethods forces JVM bytecode verification of the class's methods without running <clinit>.
			assertTrue(loaded.getDeclaredMethods().length > 0);
		}
	}

	private static final class VerifyingClassLoader extends URLClassLoader {
		private final String targetName;
		private final byte[] targetBytes;
		private final Path jeiJar;

		VerifyingClassLoader(String targetName, byte[] targetBytes, URL[] urls, Path jeiJar) {
			super(urls, JustEnoughFiltersContractTest.class.getClassLoader());
			this.targetName = targetName;
			this.targetBytes = targetBytes;
			this.jeiJar = jeiJar;
		}

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			try {
				return super.findClass(name);
			} catch (ClassNotFoundException ignored) {
				byte[] nested = findInNestedJars(name.replace('.', '/') + ".class");
				if (nested != null) {
					return defineClass(name, nested, 0, nested.length);
				}
				throw ignored;
			}
		}

		private byte[] findInNestedJars(String entryPath) {
			try (JarFile outer = new JarFile(jeiJar.toFile())) {
				var entries = outer.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (entry.getName().startsWith("META-INF/jars/") && entry.getName().endsWith(".jar")) {
						try (var jarIn = new java.util.jar.JarInputStream(outer.getInputStream(entry))) {
							JarEntry inner;
							while ((inner = jarIn.getNextJarEntry()) != null) {
								if (entryPath.equals(inner.getName())) {
									return jarIn.readAllBytes();
								}
							}
						}
					}
				}
			} catch (IOException ignored) {
			}
			return null;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				Class<?> existing = findLoadedClass(name);
				if (existing != null) {
					return existing;
				}
				if (targetName.equals(name)) {
					Class<?> defined = defineClass(name, targetBytes, 0, targetBytes.length);
					if (resolve) {
						resolveClass(defined);
					}
					return defined;
				}
				return super.loadClass(name, resolve);
			}
		}
	}

	private static AbstractInsnNode findSingleOpcode(MethodNode method, int opcode) {
		AbstractInsnNode found = null;
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == opcode) {
				assertEquals(null, found, "Expected a single opcode " + opcode + " in " + method.name);
				found = insn;
			}
		}
		assertNotNull(found, "Missing opcode " + opcode + " in " + method.name);
		return found;
	}

	private static MethodNode findMethod(ClassNode classNode, String name, String desc) {
		for (MethodNode method : classNode.methods) {
			if (name.equals(method.name) && desc.equals(method.desc)) {
				return method;
			}
		}
		return null;
	}

	private static boolean usesLocalSlot(MethodNode method, int slot) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof VarInsnNode varInsn && varInsn.var == slot) {
				return true;
			}
		}
		return false;
	}

	private static boolean overwritesSlotWithIntAndObject(MethodNode method, int slot) {
		boolean storesInt = false;
		boolean storesObject = false;
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof VarInsnNode varInsn && varInsn.var == slot) {
				if (varInsn.getOpcode() == Opcodes.ISTORE) {
					storesInt = true;
				} else if (varInsn.getOpcode() == Opcodes.ASTORE) {
					storesObject = true;
				}
			}
		}
		return storesInt && storesObject;
	}

	private static Path findJarContaining(String entryName) throws Exception {
		URL url = JustEnoughFiltersContractTest.class.getClassLoader().getResource(entryName);
		assertNotNull(url, "Missing test classpath resource: " + entryName);
		JarURLConnection connection = (JarURLConnection) url.openConnection();
		URI jarUri = connection.getJarFileURL().toURI();
		return Path.of(jarUri);
	}

	private static ClassNode readClassFromJar(JarFile jar, String entryName) throws IOException {
		JarEntry entry = jar.getJarEntry(entryName);
		assertNotNull(entry, "Missing JAR entry " + entryName);
		try (InputStream input = jar.getInputStream(entry)) {
			ClassNode node = new ClassNode();
			new ClassReader(input).accept(node, 0);
			return node;
		}
	}

	private static JsonObject readJsonFromJar(JarFile jar, String entryName) throws IOException {
		JarEntry entry = jar.getJarEntry(entryName);
		assertNotNull(entry, "Missing JAR entry " + entryName);
		try (InputStream input = jar.getInputStream(entry)) {
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream input = JustEnoughFiltersContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, resource);
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static String sha256Hex(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = Files.newInputStream(path)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static Set<String> stringSet(JsonArray array) {
		Set<String> values = new HashSet<>();
		array.forEach(element -> values.add(element.getAsString()));
		return values;
	}

	private static Map<String, Object> annotationValues(AnnotationNode annotation) {
		Map<String, Object> result = new java.util.HashMap<>();
		if (annotation.values == null) {
			return result;
		}
		for (int i = 0; i < annotation.values.size(); i += 2) {
			result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
		}
		return result;
	}

	private static ModOrigin origin(List<Path> paths, ModOrigin.Kind kind) {
		return proxy(ModOrigin.class, (proxy, method, args) -> switch (method.getName()) {
			case "getKind" -> kind;
			case "getPaths" -> paths;
			default -> null;
		});
	}

	private static ModContainer container(String modId, String version, ModOrigin origin) {
		ModMetadata metadata = proxy(ModMetadata.class, (proxy, method, args) -> switch (method.getName()) {
			case "getId" -> modId;
			case "getVersion" -> Version.parse(version);
			default -> defaultValue(method.getReturnType());
		});
		return proxy(ModContainer.class, (proxy, method, args) -> switch (method.getName()) {
			case "getOrigin" -> origin;
			case "getMetadata" -> metadata;
			default -> defaultValue(method.getReturnType());
		});
	}

	private static FabricLoader loader(Map<String, ModContainer> mods) {
		return proxy(FabricLoader.class, (proxy, method, args) -> {
			if ("getModContainer".equals(method.getName())) {
				return Optional.ofNullable(mods.get(args[0]));
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
		if (type == char.class) {
			return '\0';
		}
		return (byte) 0;
	}
}
