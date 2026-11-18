package lampas2overrides.client.signsfaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for the Signs Faster 1.0.3 blank-first-line fix. The Signs Faster class and its fabric.mod.json are read
 * with ASM and Gson from the test runtime classpath and never loaded, so none of its classes are initialized.
 */
class SignsFasterContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.signsfaster.mixins.json";
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";
	private static final String MIXIN = "lampas2overrides/client/signsfaster/mixin/SignTextureManagerMixin";
	private static final String HANDLER = "lampas2$blankOnlyWhenAllLinesEmpty";
	private static final String HANDLER_DESC = "(Ljava/lang/String;"
		+ "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;"
		+ "Lnet/minecraft/world/level/block/entity/SignText;)Z";

	private static final String VENDOR = "org/lolip_p/signfasterlegacy/util/SignTextureManager";
	private static final String VENDOR_NAME = "org.lolip_p.signfasterlegacy.util.SignTextureManager";
	private static final String GET_OR_CREATE = "getOrCreateRegion";
	private static final String GET_OR_CREATE_DESC = "(Lnet/minecraft/world/level/block/entity/SignText;)"
		+ "Lorg/lolip_p/signfasterlegacy/util/SignTextureManager$AtlasRegion;";
	private static final String GENERATE = "generate";
	private static final String GENERATE_DESC = "(Lnet/minecraft/world/level/block/entity/SignText;)"
		+ "Lcom/mojang/blaze3d/platform/NativeImage;";

	private static final String IS_EMPTY_OWNER = "java/lang/String";
	private static final String IS_EMPTY = "isEmpty";
	private static final String IS_EMPTY_DESC = "()Z";
	private static final String IS_EMPTY_TARGET = "Ljava/lang/String;isEmpty()Z";
	private static final String COMPONENT = "net/minecraft/network/chat/Component";
	private static final String GET_STRING = "getString";
	private static final String GET_STRING_DESC = "()Ljava/lang/String;";

	private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String WRAP_OPERATION_ANNOTATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";

	@Test
	void registersTheFeatureAsClientOnlyAndRequired() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("lampas2overrides.client.signsfaster.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.client.signsfaster.SignsFasterMixinPlugin", config.get("plugin").getAsString());
		assertEquals(List.of("SignTextureManagerMixin"), jsonStrings(config.getAsJsonArray("client")));
		assertFalse(config.has("mixins"), "the Signs Faster mixin must not be common");
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
		assertFalse(metadata.getAsJsonObject("depends").has("signfasterlegacy"), "signfasterlegacy must stay optional");
	}

	/** The gate compares the installed mod ID and version with these constants, so the jar must declare the same pair. */
	@Test
	void signsFasterJarDeclaresTheGatedIdAndVersion() throws IOException {
		JsonObject metadata = readVendorMetadata();
		assertEquals("signfasterlegacy", metadata.get("id").getAsString());
		assertEquals("1.0.3", metadata.get("version").getAsString());
		assertEquals(metadata.get("id").getAsString(), SignsFasterMixinPlugin.MOD_ID);
		assertEquals(metadata.get("version").getAsString(), SignsFasterMixinPlugin.AUDITED_VERSION);
	}

	/**
	 * {@code getOrCreateRegion} must test the first line's emptiness exactly once, directly after reading that line with
	 * {@code Component#getString}. The mixin wraps that single call.
	 */
	@Test
	void getOrCreateRegionTestsTheFirstLineOnceAfterReadingIt() throws IOException {
		ClassNode vendor = read(VENDOR);
		MethodNode getOrCreate = method(vendor, GET_OR_CREATE, GET_OR_CREATE_DESC);
		assertTrue((getOrCreate.access & Opcodes.ACC_STATIC) != 0, GET_OR_CREATE + " must be static");

		List<MethodInsnNode> isEmptyCalls = isEmptyCalls(getOrCreate, Opcodes.INVOKEVIRTUAL);
		assertEquals(1, isEmptyCalls.size(), GET_OR_CREATE + " must contain exactly one String.isEmpty()Z call");

		AbstractInsnNode previous = previousInstruction(isEmptyCalls.get(0));
		assertTrue(previous instanceof MethodInsnNode, "the instruction before the isEmpty call must be a method call");
		MethodInsnNode getString = (MethodInsnNode) previous;
		assertEquals(Opcodes.INVOKEINTERFACE, getString.getOpcode());
		assertEquals(COMPONENT, getString.owner);
		assertEquals(GET_STRING, getString.name);
		assertEquals(GET_STRING_DESC, getString.desc);

		// The line read is element 0 of the array: ICONST_0, AALOAD, then getString.
		AbstractInsnNode load = previousInstruction(getString);
		assertEquals(Opcodes.AALOAD, load.getOpcode(), "getString must read an array element");
		assertEquals(Opcodes.ICONST_0, previousInstruction(load).getOpcode(), "the array element read must be index 0");

		// An empty first line falls through IFEQ into "return null"; a non-empty one jumps past it.
		AbstractInsnNode branch = nextInstruction(isEmptyCalls.get(0));
		assertEquals(Opcodes.IFEQ, branch.getOpcode(), "isEmpty must be followed by IFEQ");
		AbstractInsnNode fallThrough = nextInstruction(branch);
		assertEquals(Opcodes.ACONST_NULL, fallThrough.getOpcode(), "an empty first line must return null");
		assertEquals(Opcodes.ARETURN, nextInstruction(fallThrough).getOpcode(), "an empty first line must return null");
	}

	/**
	 * The texture generator must skip an empty line and go on to the next one (a {@code continue}), not stop. The fix
	 * relies on this: a sign whose first line is blank must still get lines 2 to 4 drawn. javac compiles the continue as
	 * {@code isEmpty; IFEQ body; GOTO step}, where {@code step} is the loop's IINC followed by the backward GOTO.
	 */
	@Test
	void generateContinuesPastEachEmptyLine() throws IOException {
		ClassNode vendor = read(VENDOR);
		MethodNode generate = method(vendor, GENERATE, GENERATE_DESC);

		List<MethodInsnNode> isEmptyCalls = isEmptyCalls(generate, Opcodes.INVOKEVIRTUAL);
		assertEquals(1, isEmptyCalls.size(), GENERATE + " must contain exactly one String.isEmpty()Z call");

		AbstractInsnNode branch = nextInstruction(isEmptyCalls.get(0));
		assertEquals(Opcodes.IFEQ, branch.getOpcode(), "isEmpty must be followed by IFEQ into the line body");
		AbstractInsnNode skip = nextInstruction(branch);
		assertEquals(Opcodes.GOTO, skip.getOpcode(), "an empty line must jump away, not return or fall into the body");

		AbstractInsnNode step = nextInstruction(((JumpInsnNode) skip).label);
		assertEquals(Opcodes.IINC, step.getOpcode(), "an empty line must jump to the loop's increment");
		AbstractInsnNode back = nextInstruction(step);
		assertEquals(Opcodes.GOTO, back.getOpcode(), "the increment must be followed by the loop's back edge");
		InsnList instructions = generate.instructions;
		assertTrue(instructions.indexOf(((JumpInsnNode) back).label) < instructions.indexOf(isEmptyCalls.get(0)),
			"the back edge must return to the loop head before the isEmpty check");
	}

	/** The mixin must target the vendor class, wrap the one isEmpty call in getOrCreateRegion, and take the SignText argument. */
	@Test
	void mixinWrapsTheVendorCheckWithTheBlankCheck() throws IOException {
		ClassNode mixin = read(MIXIN);
		AnnotationNode mixinAnnotation = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, MIXIN_ANNOTATION);
		assertNotNull(mixinAnnotation, mixin.name + " must carry @Mixin");
		assertEquals(List.of(VENDOR_NAME), listOf(value(mixinAnnotation, "targets")));
		assertEquals(Boolean.FALSE, value(mixinAnnotation, "remap"));

		MethodNode handler = method(mixin, HANDLER, HANDLER_DESC);
		assertTrue((handler.access & Opcodes.ACC_STATIC) != 0, HANDLER + " must be static");
		AnnotationNode wrap = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, WRAP_OPERATION_ANNOTATION);
		assertNotNull(wrap, HANDLER + " must carry @WrapOperation");
		assertEquals(List.of(GET_OR_CREATE), listOf(value(wrap, "method")));

		List<?> ats = (List<?>) value(wrap, "at");
		assertNotNull(ats, HANDLER + " must set @At");
		assertEquals(1, ats.size(), HANDLER + " must have one @At");
		AnnotationNode at = (AnnotationNode) ats.get(0);
		assertEquals("INVOKE", value(at, "value"));
		assertEquals(IS_EMPTY_TARGET, value(at, "target"));
	}

	/** Returns the isEmpty()Z calls on String with the given opcode, in instruction order. */
	private static List<MethodInsnNode> isEmptyCalls(MethodNode method, int opcode) {
		List<MethodInsnNode> calls = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == opcode
				&& call.owner.equals(IS_EMPTY_OWNER) && call.name.equals(IS_EMPTY) && call.desc.equals(IS_EMPTY_DESC)) {
				calls.add(call);
			}
		}
		return calls;
	}

	/** Returns the nearest preceding instruction with an opcode, skipping labels, line numbers and frames (opcode -1). */
	private static AbstractInsnNode previousInstruction(AbstractInsnNode insn) {
		AbstractInsnNode previous = insn.getPrevious();
		while (previous != null && previous.getOpcode() == -1) {
			previous = previous.getPrevious();
		}
		return previous;
	}

	/** Returns the nearest following instruction with an opcode, skipping labels, line numbers and frames (opcode -1). */
	private static AbstractInsnNode nextInstruction(AbstractInsnNode insn) {
		AbstractInsnNode next = insn.getNext();
		while (next != null && next.getOpcode() == -1) {
			next = next.getNext();
		}
		return next;
	}

	/** Opens the Signs Faster jar that holds the vendor class. Caching is off, so the caller owns and closes the JarFile. */
	private static JarFile vendorJar() throws IOException {
		URL url = SignsFasterContractTest.class.getClassLoader().getResource(VENDOR + ".class");
		assertNotNull(url, "Signs Faster must be on the test classpath");
		assertEquals("jar", url.getProtocol(), "the vendor class must come from a jar");
		JarURLConnection connection = (JarURLConnection) url.openConnection();
		connection.setUseCaches(false);
		return connection.getJarFile();
	}

	private static JsonObject readVendorMetadata() throws IOException {
		try (JarFile jar = vendorJar()) {
			ZipEntry entry = jar.getEntry(FABRIC_MOD_JSON);
			assertNotNull(entry, "the Signs Faster jar must declare " + FABRIC_MOD_JSON);
			try (InputStream in = jar.getInputStream(entry)) {
				return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
		}
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

	private static ClassNode read(String internalName) throws IOException {
		try (InputStream input = SignsFasterContractTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			assertNotNull(input, internalName + " must be on the test classpath");
			ClassNode node = new ClassNode();
			new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return node;
		}
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream in = SignsFasterContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(in, "Missing " + resource + " on the test classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
