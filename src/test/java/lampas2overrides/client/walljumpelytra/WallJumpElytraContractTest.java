package lampas2overrides.client.walljumpelytra;

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
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for the Wall-Jump TXF elytra suppression hook. The Wall-Jump TXF classes and its fabric.mod.json are
 * inspected with ASM and Gson from the test runtime classpath without initializing any vendor class.
 */
class WallJumpElytraContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.walljumpelytra.mixins.json";
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";
	private static final String OUR_MIXIN = "lampas2overrides/client/walljumpelytra/mixin/LocalPlayerElytraMixin";
	private static final String OUR_WALL_JUMP_MIXIN =
		"lampas2overrides/client/walljumpelytra/mixin/WallJumpLogicMixin";
	private static final String OUR_DOUBLE_JUMP_MIXIN =
		"lampas2overrides/client/walljumpelytra/mixin/DoubleJumpLogicMixin";

	private static final String VENDOR_CLIENT = "com/jahirtrap/walljump/WallJumpClient";
	private static final String VENDOR_CONFIG = "com/jahirtrap/walljump/init/ModConfig";
	private static final String VENDOR_BLOCK_LIST_MODE = "com/jahirtrap/walljump/init/ModConfig$BlockListMode";
	private static final String VENDOR_WALL_JUMP_LOGIC = "com/jahirtrap/walljump/logic/WallJumpLogic";
	private static final String VENDOR_WALL_JUMP_LOGIC_NAME = "com.jahirtrap.walljump.logic.WallJumpLogic";
	private static final String VENDOR_DOUBLE_JUMP_LOGIC = "com/jahirtrap/walljump/logic/DoubleJumpLogic";
	private static final String VENDOR_DOUBLE_JUMP_LOGIC_NAME = "com.jahirtrap.walljump.logic.DoubleJumpLogic";
	private static final String VENDOR_MIXIN = "com/jahirtrap/walljump/init/mixin/LocalPlayerMixin";

	private static final String LOCAL_PLAYER = "net/minecraft/client/player/LocalPlayer";
	private static final String START_FALL_FLYING_ACTION =
		"net/minecraft/network/protocol/game/ServerboundPlayerCommandPacket$Action";

	private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String INJECT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String WRAP_OPERATION_ANNOTATION =
		"Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";

	@Test
	void registersFeatureAsClientOnlyAndOptional() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("lampas2overrides.client.walljumpelytra.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.client.walljumpelytra.WallJumpElytraMixinPlugin", config.get("plugin").getAsString());
		assertEquals(
			List.of("LocalPlayerElytraMixin", "WallJumpLogicMixin", "DoubleJumpLogicMixin"),
			jsonStrings(config.getAsJsonArray("client"))
		);
		assertFalse(config.has("mixins"), "the Wall-Jump elytra mixin must not be common");
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
		assertTrue(
			jsonStrings(metadata.getAsJsonObject("entrypoints").getAsJsonArray("main"))
				.contains("lampas2overrides.walljumpelytra.WallJumpElytraNetworking"),
			"fabric.mod.json must register WallJumpElytraNetworking as a main entrypoint"
		);
		assertFalse(metadata.getAsJsonObject("depends").has("walljump"), "walljump must stay optional");
		assertTrue(metadata.getAsJsonObject("suggests").has("walljump"), "walljump should be listed in suggests");
	}

	@Test
	void wallJumpJarDeclaresAuditedIdAndVersion() throws IOException {
		JsonObject metadata = readVendorMetadata();
		assertEquals(WallJumpElytraMixinPlugin.MOD_ID, metadata.get("id").getAsString());
		assertEquals(WallJumpElytraMixinPlugin.AUDITED_VERSION, metadata.get("version").getAsString());
	}

	@Test
	void wallJumpReflectedMembersMatchPinnedJarContract() throws IOException {
		ClassNode client = read(VENDOR_CLIENT);
		assertStaticField(client, "KEY_WALL_JUMP", "Lnet/minecraft/client/KeyMapping;");

		ClassNode config = read(VENDOR_CONFIG);
		assertStaticField(config, "enableEnchantments", "Z");
		assertStaticField(config, "enableDoubleJump", "Z");
		assertStaticField(config, "useDoubleJump", "Z");
		assertStaticField(config, "onFallDoubleJump", "Z");
		assertStaticField(config, "blockList", "Ljava/util/List;");
		assertStaticField(config, "blockListMode", "L" + VENDOR_BLOCK_LIST_MODE + ";");

		ClassNode mode = read(VENDOR_BLOCK_LIST_MODE);
		assertTrue((mode.access & Opcodes.ACC_ENUM) != 0, "ModConfig$BlockListMode must be an enum");
		assertStaticField(mode, "DISABLED", "L" + VENDOR_BLOCK_LIST_MODE + ";");
		assertStaticField(mode, "BLACKLIST", "L" + VENDOR_BLOCK_LIST_MODE + ";");
		assertStaticField(mode, "WHITELIST", "L" + VENDOR_BLOCK_LIST_MODE + ";");

		ClassNode wallJump = read(VENDOR_WALL_JUMP_LOGIC);
		assertStaticField(wallJump, "ticksWallClinged", "I");
		assertStaticField(wallJump, "wallJumpCount", "I");
		assertStaticField(wallJump, "stopSlid", "Z");
		assertStaticField(wallJump, "ticksKeyDown", "I");
		MethodNode canWallJump = method(wallJump, "canWallJump", "(L" + LOCAL_PLAYER + ";)Z");
		assertTrue((canWallJump.access & Opcodes.ACC_STATIC) != 0, "canWallJump must be static");

		ClassNode doubleJump = read(VENDOR_DOUBLE_JUMP_LOGIC);
		assertStaticField(doubleJump, "jumpCount", "I");
		assertStaticField(doubleJump, "jumpKey", "Z");
	}

	/**
	 * Wall-Jump TXF runs {@code doWallJump} and {@code doDoubleJump} at {@code aiStep} {@code TAIL}. Our policy assumes
	 * those updates finish after vanilla's {@code tryToStartFallFlying()} and are visible by {@code HEAD} of the next
	 * tick.
	 */
	@Test
	void wallJumpRunsAtLocalPlayerAiStepTail() throws IOException {
		ClassNode vendorMixin = read(VENDOR_MIXIN);
		MethodNode aiStep = method(vendorMixin, "aiStep", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
		AnnotationNode inject = annotation(aiStep.visibleAnnotations, aiStep.invisibleAnnotations, INJECT_ANNOTATION);
		assertNotNull(inject, "Wall-Jump LocalPlayerMixin#aiStep must carry @Inject");
		assertEquals(List.of("aiStep"), listOf(value(inject, "method")));
		List<?> ats = (List<?>) value(inject, "at");
		assertNotNull(ats);
		assertEquals(1, ats.size());
		assertEquals("TAIL", value((AnnotationNode) ats.get(0), "value"));

		boolean callsWallJump = false;
		boolean callsDoubleJump = false;
		for (AbstractInsnNode insn : aiStep.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC) {
				if (VENDOR_WALL_JUMP_LOGIC.equals(call.owner) && "doWallJump".equals(call.name)) {
					callsWallJump = true;
				} else if (VENDOR_DOUBLE_JUMP_LOGIC.equals(call.owner) && "doDoubleJump".equals(call.name)) {
					callsDoubleJump = true;
				}
			}
		}
		assertTrue(callsWallJump, "Wall-Jump LocalPlayerMixin#aiStep must invoke WallJumpLogic.doWallJump");
		assertTrue(callsDoubleJump, "Wall-Jump LocalPlayerMixin#aiStep must invoke DoubleJumpLogic.doDoubleJump");
	}

	@Test
	void localPlayerAiStepCallsTryToStartFallFlyingOnceAndOurMixinMatchesIt() throws IOException {
		ClassNode localPlayer = read(LOCAL_PLAYER);
		MethodNode aiStep = method(localPlayer, "aiStep", "()V");
		List<MethodInsnNode> tryCalls = new ArrayList<>();
		for (AbstractInsnNode insn : aiStep.instructions) {
			if (insn instanceof MethodInsnNode call
				&& call.getOpcode() == Opcodes.INVOKEVIRTUAL
				&& LOCAL_PLAYER.equals(call.owner)
				&& "tryToStartFallFlying".equals(call.name)
				&& "()Z".equals(call.desc)) {
				tryCalls.add(call);
			}
		}
		assertEquals(1, tryCalls.size(), "LocalPlayer#aiStep must call LocalPlayer.tryToStartFallFlying()Z exactly once");

		boolean sendsStartFallFlyingAfter = false;
		for (AbstractInsnNode insn = tryCalls.get(0).getNext(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode field
				&& START_FALL_FLYING_ACTION.equals(field.owner)
				&& "START_FALL_FLYING".equals(field.name)) {
				sendsStartFallFlyingAfter = true;
				break;
			}
		}
		assertTrue(sendsStartFallFlyingAfter, "LocalPlayer#aiStep must send START_FALL_FLYING after tryToStartFallFlying");

		ClassNode ourMixin = read(OUR_MIXIN);
		AnnotationNode mixinAnnotation = annotation(ourMixin.visibleAnnotations, ourMixin.invisibleAnnotations, MIXIN_ANNOTATION);
		assertNotNull(mixinAnnotation, OUR_MIXIN + " must carry @Mixin");
		assertEquals(List.of(Type.getObjectType(LOCAL_PLAYER)), (List<?>) value(mixinAnnotation, "value"));

		MethodNode headHook = method(
			ourMixin,
			"lampas2$trackWallJumpState",
			"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"
		);
		AnnotationNode headInject = annotation(headHook.visibleAnnotations, headHook.invisibleAnnotations, INJECT_ANNOTATION);
		assertNotNull(headInject);
		assertEquals(List.of("aiStep"), listOf(value(headInject, "method")));
		AnnotationNode headAt = (AnnotationNode) ((List<?>) value(headInject, "at")).get(0);
		assertEquals("HEAD", value(headAt, "value"));

		MethodNode wrapHook = method(
			ourMixin,
			"lampas2$suppressElytraDuringWallJump",
			"(Lnet/minecraft/client/player/LocalPlayer;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Z"
		);
		AnnotationNode wrap = annotation(wrapHook.visibleAnnotations, wrapHook.invisibleAnnotations, WRAP_OPERATION_ANNOTATION);
		assertNotNull(wrap);
		assertEquals(List.of("aiStep"), listOf(value(wrap, "method")));
		AnnotationNode wrapAt = (AnnotationNode) ((List<?>) value(wrap, "at")).get(0);
		assertEquals("INVOKE", value(wrapAt, "value"));
		assertEquals("Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z", value(wrapAt, "target"));
	}

	@Test
	void wallJumpAndDoubleJumpMixinsMatchPinnedVendorContracts() throws IOException {
		ClassNode wallJump = read(VENDOR_WALL_JUMP_LOGIC);
		MethodNode doWallJump = method(wallJump, "doWallJump", "(L" + LOCAL_PLAYER + ";)V");
		List<MethodInsnNode> handsBusyCalls = invokeVirtualCalls(doWallJump, LOCAL_PLAYER, "isHandsBusy", "()Z");
		assertEquals(1, handsBusyCalls.size(), "WallJumpLogic#doWallJump must call isHandsBusy()Z once");

		ClassNode ourWallJumpMixin = read(OUR_WALL_JUMP_MIXIN);
		AnnotationNode wallJumpMixinAnn =
			annotation(ourWallJumpMixin.visibleAnnotations, ourWallJumpMixin.invisibleAnnotations, MIXIN_ANNOTATION);
		assertNotNull(wallJumpMixinAnn);
		assertEquals(List.of(VENDOR_WALL_JUMP_LOGIC_NAME), listOf(value(wallJumpMixinAnn, "targets")));
		assertEquals(Boolean.FALSE, value(wallJumpMixinAnn, "remap"));
		MethodNode wallJumpWrap = method(
			ourWallJumpMixin,
			"lampas2$blockWallClingWhileGliding",
			"(Lnet/minecraft/client/player/LocalPlayer;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Z"
		);
		assertTrue((wallJumpWrap.access & Opcodes.ACC_STATIC) != 0);
		AnnotationNode wjWrap =
			annotation(wallJumpWrap.visibleAnnotations, wallJumpWrap.invisibleAnnotations, WRAP_OPERATION_ANNOTATION);
		assertNotNull(wjWrap);
		assertEquals(List.of("doWallJump"), listOf(value(wjWrap, "method")));
		AnnotationNode wjAt = (AnnotationNode) ((List<?>) value(wjWrap, "at")).get(0);
		assertEquals("INVOKE", value(wjAt, "value"));
		assertEquals("Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z", value(wjAt, "target"));

		ClassNode doubleJump = read(VENDOR_DOUBLE_JUMP_LOGIC);
		MethodNode doDoubleJump = method(doubleJump, "doDoubleJump", "(L" + LOCAL_PLAYER + ";)V");
		List<MethodInsnNode> passengerCalls = invokeVirtualCalls(doDoubleJump, LOCAL_PLAYER, "isPassenger", "()Z");
		assertEquals(1, passengerCalls.size(), "DoubleJumpLogic#doDoubleJump must call isPassenger()Z once");

		ClassNode ourDoubleJumpMixin = read(OUR_DOUBLE_JUMP_MIXIN);
		AnnotationNode doubleJumpMixinAnn =
			annotation(ourDoubleJumpMixin.visibleAnnotations, ourDoubleJumpMixin.invisibleAnnotations, MIXIN_ANNOTATION);
		assertNotNull(doubleJumpMixinAnn);
		assertEquals(List.of(VENDOR_DOUBLE_JUMP_LOGIC_NAME), listOf(value(doubleJumpMixinAnn, "targets")));
		assertEquals(Boolean.FALSE, value(doubleJumpMixinAnn, "remap"));
		MethodNode doubleJumpWrap = method(
			ourDoubleJumpMixin,
			"lampas2$blockDoubleJumpWhileGliding",
			"(Lnet/minecraft/client/player/LocalPlayer;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Z"
		);
		assertTrue((doubleJumpWrap.access & Opcodes.ACC_STATIC) != 0);
		AnnotationNode djWrap =
			annotation(doubleJumpWrap.visibleAnnotations, doubleJumpWrap.invisibleAnnotations, WRAP_OPERATION_ANNOTATION);
		assertNotNull(djWrap);
		assertEquals(List.of("doDoubleJump"), listOf(value(djWrap, "method")));
		AnnotationNode djAt = (AnnotationNode) ((List<?>) value(djWrap, "at")).get(0);
		assertEquals("INVOKE", value(djAt, "value"));
		assertEquals("Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z", value(djAt, "target"));
	}

	private static List<MethodInsnNode> invokeVirtualCalls(
		MethodNode method,
		String owner,
		String name,
		String desc
	) {
		List<MethodInsnNode> calls = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call
				&& call.getOpcode() == Opcodes.INVOKEVIRTUAL
				&& owner.equals(call.owner)
				&& name.equals(call.name)
				&& desc.equals(call.desc)) {
				calls.add(call);
			}
		}
		return calls;
	}

	private static void assertStaticField(ClassNode owner, String name, String desc) {
		for (FieldNode field : owner.fields) {
			if (field.name.equals(name) && field.desc.equals(desc)) {
				assertTrue((field.access & Opcodes.ACC_STATIC) != 0, owner.name + "." + name + " must be static");
				return;
			}
		}
		throw new AssertionError("Missing field " + owner.name + "." + name + ":" + desc);
	}

	private static JarFile vendorJar() throws IOException {
		URL url = WallJumpElytraContractTest.class.getClassLoader().getResource(VENDOR_CLIENT + ".class");
		assertNotNull(url, "Wall-Jump TXF must be on the test classpath");
		assertEquals("jar", url.getProtocol(), "Wall-Jump TXF class must come from a jar");
		JarURLConnection connection = (JarURLConnection) url.openConnection();
		connection.setUseCaches(false);
		return connection.getJarFile();
	}

	private static JsonObject readVendorMetadata() throws IOException {
		try (JarFile jar = vendorJar()) {
			ZipEntry entry = jar.getEntry(FABRIC_MOD_JSON);
			assertNotNull(entry, "Wall-Jump TXF jar must declare " + FABRIC_MOD_JSON);
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

	private static List<String> listOf(Object value) {
		List<String> found = new ArrayList<>();
		for (Object item : (List<?>) value) {
			found.add((String) item);
		}
		return found;
	}

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
		try (InputStream input = WallJumpElytraContractTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			assertNotNull(input, internalName + " must be on the test classpath");
			ClassNode node = new ClassNode();
			new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return node;
		}
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream in = WallJumpElytraContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(in, "Missing " + resource + " on the test classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
