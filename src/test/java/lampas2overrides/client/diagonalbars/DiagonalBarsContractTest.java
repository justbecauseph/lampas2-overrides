package lampas2overrides.client.diagonalbars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for the Diagonal Blocks 26.2.0 rotated bars post fix. The Diagonal Blocks jar is nested in the pinned
 * Diagonal Windows jar on the test runtime classpath. It is read with ASM and Gson and never loaded, so none of its classes
 * are initialized.
 */
class DiagonalBarsContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.diagonalbars.mixins.json";
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";
	private static final String MIXIN = "lampas2overrides/client/diagonalbars/mixin/MultiPartAppenderMixin";
	private static final String HANDLER = "lampas2$hideRotatedBarsPost";
	private static final String HANDLER_DESC = "(Lnet/minecraft/client/renderer/block/dispatch/multipart/Condition;"
		+ "Lnet/minecraft/client/renderer/block/dispatch/multipart/Selector;)"
		+ "Lnet/minecraft/client/renderer/block/dispatch/multipart/Condition;";
	private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String MODIFY_ARG_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
	private static final String LOCAL_ANNOTATION = "Lcom/llamalad7/mixinextras/sugar/Local;";

	private static final String NESTED_JAR = "META-INF/jars/diagonalblocks-fabric-26.2.0.jar";
	private static final String VENDOR = "fuzs/diagonalblocks/common/impl/client/resources/model/MultiPartAppender";
	private static final String VENDOR_CLASS_FILE = VENDOR + ".class";
	private static final String VENDOR_NAME = "fuzs.diagonalblocks.common.impl.client.resources.model.MultiPartAppender";
	private static final String APPEND = "appendDiagonalSelectors";
	private static final String APPEND_DESC = "(Lnet/minecraft/client/renderer/block/dispatch/BlockStateModelDispatcher$MultiPartDefinition;Z)"
		+ "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModelDispatcher$MultiPartDefinition;";
	private static final String APPEND_SELECTOR = "appendNewSelector";
	private static final String APPEND_SELECTOR_DESC = "(Lnet/minecraft/client/renderer/block/dispatch/multipart/Condition;"
		+ "Lnet/minecraft/client/renderer/block/dispatch/multipart/Selector;"
		+ "Lfuzs/diagonalblocks/common/api/v2/util/EightWayDirection;Ljava/util/List;)V";
	private static final String GET_AND_CONDITION = "getAndCondition";
	private static final String GET_AND_CONDITION_DESC = "([Lfuzs/diagonalblocks/common/api/v2/util/EightWayDirection;)"
		+ "Lnet/minecraft/client/renderer/block/dispatch/multipart/Condition;";
	/** The vendor owner, name and descriptor as the mixin's {@code @At} target spells them. */
	private static final String APPEND_SELECTOR_TARGET = "L" + VENDOR + ";" + APPEND_SELECTOR + APPEND_SELECTOR_DESC;

	@Test
	void registersTheFeatureAsClientOnlyAndRequired() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("lampas2overrides.client.diagonalbars.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.client.diagonalbars.DiagonalBarsMixinPlugin", config.get("plugin").getAsString());
		assertEquals(List.of("MultiPartAppenderMixin"), jsonStrings(config.getAsJsonArray("client")));
		assertFalse(config.has("mixins"), "the Diagonal Blocks mixin must not be common");
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

		JsonObject metadata = readJson(FABRIC_MOD_JSON);
		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			if (MIXIN_CONFIG.equals(entry.getAsJsonObject().get("config").getAsString())) {
				registered = entry.getAsJsonObject();
			}
		}
		assertNotNull(registered, "fabric.mod.json must register " + MIXIN_CONFIG);
		assertEquals("client", registered.get("environment").getAsString());
		assertFalse(metadata.getAsJsonObject("depends").has("diagonalblocks"), "diagonalblocks must stay optional");
	}

	/** The gate compares the installed mod ID and version with these constants, so the nested jar must declare the same pair. */
	@Test
	void nestedDiagonalBlocksJarDeclaresTheGatedIdAndVersion() throws IOException {
		Map<String, byte[]> entries = nestedJarEntries(FABRIC_MOD_JSON);
		JsonObject metadata = JsonParser.parseString(new String(entries.get(FABRIC_MOD_JSON), StandardCharsets.UTF_8))
			.getAsJsonObject();
		assertEquals("diagonalblocks", metadata.get("id").getAsString());
		assertEquals("26.2.0", metadata.get("version").getAsString());
		assertEquals(metadata.get("id").getAsString(), DiagonalBarsMixinPlugin.MOD_ID);
		assertEquals(metadata.get("version").getAsString(), DiagonalBarsMixinPlugin.AUDITED_VERSION);
	}

	/**
	 * {@code appendDiagonalSelectors} makes three {@code appendNewSelector} calls. Call 1 (ordinal 1) is the else-branch call,
	 * which takes the result of the one {@code getAndCondition} call in the method. That call sits between calls 0 and 1.
	 */
	@Test
	void appendDiagonalSelectorsBuildsTheRotatedCopyAfterOneGetAndCondition() throws IOException {
		MethodNode append = method(readVendor(), APPEND, APPEND_DESC);

		List<MethodInsnNode> selectorCalls = calls(append, APPEND_SELECTOR, APPEND_SELECTOR_DESC);
		assertEquals(3, selectorCalls.size(), APPEND + " must make three " + APPEND_SELECTOR + " calls");

		List<MethodInsnNode> andCalls = calls(append, GET_AND_CONDITION, GET_AND_CONDITION_DESC);
		assertEquals(1, andCalls.size(), APPEND + " must call " + GET_AND_CONDITION + " exactly once");

		int firstSelector = append.instructions.indexOf(selectorCalls.get(0));
		int andCondition = append.instructions.indexOf(andCalls.get(0));
		int secondSelector = append.instructions.indexOf(selectorCalls.get(1));
		assertTrue(firstSelector < andCondition && andCondition < secondSelector,
			GET_AND_CONDITION + " must sit between " + APPEND_SELECTOR + " calls 0 and 1");
	}

	/**
	 * The handler wraps argument 0 of the second {@code appendNewSelector} call and takes the source selector with
	 * {@code @Local(ordinal = 0)}, the loop variable. The {@code @At} target names that exact call.
	 */
	@Test
	void mixinWrapsTheRotatedCopyAtOrdinalOneWithTheSourceSelector() throws IOException {
		ClassNode mixin = readClass(MIXIN);
		AnnotationNode mixinAnnotation = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, MIXIN_ANNOTATION);
		assertNotNull(mixinAnnotation, mixin.name + " must carry @Mixin");
		assertEquals(List.of(VENDOR_NAME), listOf(value(mixinAnnotation, "targets")));
		assertEquals(Boolean.FALSE, value(mixinAnnotation, "remap"));

		MethodNode handler = method(mixin, HANDLER, HANDLER_DESC);
		assertTrue((handler.access & Opcodes.ACC_STATIC) != 0, HANDLER + " must be static");
		AnnotationNode modify = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, MODIFY_ARG_ANNOTATION);
		assertNotNull(modify, HANDLER + " must carry @ModifyArg");
		assertEquals(List.of(APPEND), listOf(value(modify, "method")));
		assertEquals(Integer.valueOf(0), value(modify, "index"));

		// Mixin 0.17.4 and later declare ModifyArg.at as At[], so javac stores the single @At as a one-element array.
		List<?> ats = (List<?>) value(modify, "at");
		assertNotNull(ats, HANDLER + " must set @At");
		assertEquals(1, ats.size(), HANDLER + " must have one @At");
		AnnotationNode at = (AnnotationNode) ats.get(0);
		assertEquals("INVOKE", value(at, "value"));
		assertEquals(APPEND_SELECTOR_TARGET, value(at, "target"));
		assertEquals(Integer.valueOf(1), value(at, "ordinal"));

		AnnotationNode local = parameterAnnotation(handler, 1, LOCAL_ANNOTATION);
		assertNotNull(local, HANDLER + " must take the source selector with @Local");
		assertEquals(Integer.valueOf(0), value(local, "ordinal"));
	}

	/** Returns the static calls in {@code method} to {@code VENDOR.name desc}, in instruction order. */
	private static List<MethodInsnNode> calls(MethodNode method, String name, String desc) {
		List<MethodInsnNode> found = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
				&& call.owner.equals(VENDOR) && call.name.equals(name) && call.desc.equals(desc)) {
				found.add(call);
			}
		}
		return found;
	}

	/** Reads the named entries of the Diagonal Blocks jar nested in the Diagonal Windows jar on the test classpath. */
	private static Map<String, byte[]> nestedJarEntries(String... names) throws IOException {
		List<String> wanted = List.of(names);
		Map<String, byte[]> found = new HashMap<>();
		try (InputStream outer = DiagonalBarsContractTest.class.getClassLoader().getResourceAsStream(NESTED_JAR)) {
			assertNotNull(outer, NESTED_JAR + " must be on the test classpath inside the Diagonal Windows jar");
			try (ZipInputStream nested = new ZipInputStream(outer)) {
				for (ZipEntry entry = nested.getNextEntry(); entry != null; entry = nested.getNextEntry()) {
					if (wanted.contains(entry.getName())) {
						found.put(entry.getName(), nested.readAllBytes());
					}
				}
			}
		}
		for (String name : names) {
			assertNotNull(found.get(name), name + " must be inside " + NESTED_JAR);
		}
		return found;
	}

	private static ClassNode readVendor() throws IOException {
		return classNode(nestedJarEntries(VENDOR_CLASS_FILE).get(VENDOR_CLASS_FILE));
	}

	private static MethodNode method(ClassNode owner, String name, String descriptor) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name) && method.desc.equals(descriptor)) {
				return method;
			}
		}
		throw new AssertionError(owner.name + "." + name + descriptor + " is not declared");
	}

	/** Returns the element value the source sets, or null when it keeps the default. */
	private static Object value(AnnotationNode annotation, String name) {
		if (annotation.values == null) {
			return null;
		}
		for (int i = 0; i < annotation.values.size(); i += 2) {
			if (name.equals(annotation.values.get(i))) {
				return annotation.values.get(i + 1);
			}
		}
		return null;
	}

	/** ASM stores every array-valued annotation element as a List. */
	private static List<String> listOf(Object value) {
		List<String> found = new ArrayList<>();
		for (Object item : (List<?>) value) {
			found.add((String) item);
		}
		return found;
	}

	/** Mixin's annotations are CLASS retention, so ASM keeps them in the invisible list. Both lists are searched. */
	private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String descriptor) {
		AnnotationNode found = find(visible, descriptor);
		return found != null ? found : find(invisible, descriptor);
	}

	/** Returns the annotation on one parameter, searching both the visible and the invisible lists. */
	private static AnnotationNode parameterAnnotation(MethodNode method, int parameter, String descriptor) {
		AnnotationNode found = null;
		if (method.visibleParameterAnnotations != null && parameter < method.visibleParameterAnnotations.length) {
			found = find(method.visibleParameterAnnotations[parameter], descriptor);
		}
		if (found == null && method.invisibleParameterAnnotations != null
			&& parameter < method.invisibleParameterAnnotations.length) {
			found = find(method.invisibleParameterAnnotations[parameter], descriptor);
		}
		return found;
	}

	private static AnnotationNode find(List<AnnotationNode> annotations, String descriptor) {
		if (annotations == null) {
			return null;
		}
		for (AnnotationNode annotation : annotations) {
			if (annotation.desc.equals(descriptor)) {
				return annotation;
			}
		}
		return null;
	}

	private static List<String> jsonStrings(JsonArray array) {
		List<String> found = new ArrayList<>();
		for (JsonElement element : array) {
			found.add(element.getAsString());
		}
		return found;
	}

	private static ClassNode readClass(String internalName) throws IOException {
		try (InputStream input = DiagonalBarsContractTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			assertNotNull(input, internalName + " must be on the test classpath");
			return classNode(input.readAllBytes());
		}
	}

	private static ClassNode classNode(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return node;
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream in = DiagonalBarsContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(in, "Missing " + resource + " on the test classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
