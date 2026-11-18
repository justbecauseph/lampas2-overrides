package lampas2overrides.customnamepermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for the Custom Name 0.4.4-26.2 {@code /name other} gate.
 *
 * <p>Structural checks read the compiled mixin and the pinned Custom Name jar with ASM, the same way
 * {@code CustomNameSpacesMixinTest} does. The jar checks pin what the fix relies on: {@code permissionCheck} branches on
 * {@code requirePermissions()}, its true branch is {@code CommonPermissions.require(node, GAMEMASTERS)}, the {@code other}
 * node reaches it through {@code permissionCheck}, and {@code BYPASS_RESTRICTIONS} never does. Whether the gate holds in
 * a running server is checked by {@code CustomNameOtherPermissionGameTests}.
 */
public class CustomNamePermissionsMixinTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.customnamepermissions.mixins.json";
	private static final String MIXIN_CLASS =
		"lampas2overrides/customnamepermissions/mixin/CustomNameCommandsMixin.class";
	private static final String COMMANDS_OWNER = "xyz/eclipseisoffline/eclipsescustomname/CustomNameCommands";
	private static final String COMMANDS_CLASS = COMMANDS_OWNER + ".class";
	private static final String PERMISSIONS_OWNER = "xyz/eclipseisoffline/eclipsescustomname/CustomNamePermissions";
	private static final String PERMISSIONS_CLASS = PERMISSIONS_OWNER + ".class";
	private static final String PACKAGE_PREFIX = "xyz/eclipseisoffline/eclipsescustomname/";
	private static final String PLAYER_NAME_MANAGER = "xyz/eclipseisoffline/eclipsescustomname/PlayerNameManager";
	private static final String CUSTOM_NAME_UTIL = "xyz/eclipseisoffline/eclipsescustomname/CustomNameUtil";
	private static final String CONFIG_CLASS = "xyz/eclipseisoffline/eclipsescustomname/CustomNameConfig";
	private static final String NODE_TYPE = "Lxyz/eclipseisoffline/commonpermissionsapi/api/CommonPermissionNode;";
	private static final String COMMON_PERMISSIONS = "xyz/eclipseisoffline/commonpermissionsapi/api/CommonPermissions";
	private static final String PERMISSION_CHECK = "permissionCheck";
	private static final String PERMISSION_CHECK_DESC = "(" + NODE_TYPE + ")Ljava/util/function/Predicate;";
	private static final String REQUIRE_DESC = "(" + NODE_TYPE + "Lnet/minecraft/server/permissions/PermissionLevel;)"
		+ "Ljava/util/function/Predicate;";
	private static final String CHECK_PERMISSION_DESC = "(Lnet/minecraft/commands/CommandSourceStack;" + NODE_TYPE + ")Z";

	// ── Config document and registration ──────────────────────────────────────

	@Test
	void mixinConfigExists() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
		}
	}

	@Test
	void mixinConfigReferencesPlugin() throws IOException {
		assertTrue(readMixinConfig().contains("lampas2overrides.customnamepermissions.CustomNamePermissionsMixinPlugin"),
			"config must reference CustomNamePermissionsMixinPlugin");
	}

	@Test
	void mixinConfigReferencesMixin() throws IOException {
		assertTrue(readMixinConfig().contains("CustomNameCommandsMixin"),
			"config must reference CustomNameCommandsMixin");
	}

	@Test
	void mixinConfigUsesJava25() throws IOException {
		assertTrue(readMixinConfig().contains("JAVA_25"), "config must declare JAVA_25 compatibility level");
	}

	@Test
	void mixinConfigHasDefaultRequireOne() throws IOException {
		assertTrue(readMixinConfig().contains("\"defaultRequire\": 1"),
			"config must set defaultRequire: 1 so a moved permissionCheck fails startup instead of silently opening /name other");
	}

	@Test
	void configIsRegisteredAsACommonMixinConfig() throws IOException {
		JsonObject fabric;
		try (InputStream input = resource("fabric.mod.json")) {
			assertNotNull(input, "fabric.mod.json must exist on the classpath");
			fabric = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		JsonArray mixins = fabric.getAsJsonArray("mixins");
		boolean found = false;
		for (var element : mixins) {
			JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
			if (entry != null && MIXIN_CONFIG.equals(entry.get("config").getAsString())) {
				found = true;
				assertFalse(entry.has("environment"), MIXIN_CONFIG + " must be common-side, so it has no environment");
			}
		}
		assertTrue(found, MIXIN_CONFIG + " must be listed in fabric.mod.json");
	}

	// ── Version handling ──────────────────────────────────────────────────────

	/**
	 * The audited release is exactly "0.4.4-26.2". The plugin applies the gate to every installed version, so this constant
	 * only decides the INFO versus WARN log line. Change it only after inspecting the new jar with javap and re-reading the
	 * contract checks below against it.
	 */
	@Test
	void auditedVersionIsExactAndSelectsTheLogLine() {
		assertEquals("0.4.4-26.2", CustomNamePermissionsMixinPlugin.AUDITED_VERSION,
			"the audited release must be exactly 0.4.4-26.2; update only after inspecting the new jar");
		assertTrue(CustomNamePermissionsMixinPlugin.isAudited("0.4.4-26.2"));
		assertFalse(CustomNamePermissionsMixinPlugin.isAudited("0.4.5-26.2"),
			"a later release is not the audited one and must log the unaudited WARN");
		assertFalse(CustomNamePermissionsMixinPlugin.isAudited("0.4.4-26.2+build"),
			"the match is exact, not a prefix");
	}

	// ── Compiled mixin structure (ASM) ────────────────────────────────────────

	@Test
	void mixinInjectsCancellableAtHeadOfPermissionCheck() throws IOException {
		ClassScan mixin = scan(MIXIN_CLASS, true);

		Map<String, Object> mixinAnnotation = mixin.classAnnotation("Lorg/spongepowered/asm/mixin/Mixin;");
		assertNotNull(mixinAnnotation, "@Mixin must be present");
		assertEquals(List.of("xyz.eclipseisoffline.eclipsescustomname.CustomNameCommands"),
			mixinAnnotation.get("targets"), "@Mixin must target CustomNameCommands");
		assertEquals(Boolean.FALSE, mixinAnnotation.get("remap"), "@Mixin must set remap = false");

		MethodScan handler = mixin.onlyInjector();
		assertEquals("lampas2$requireGamemastersForOther", handler.name,
			"the @Inject handler name is part of the contract with the mixin config");
		assertTrue((handler.access & Opcodes.ACC_STATIC) != 0, "the handler must be static to inject into a static method");
		assertEquals("(Ljava/lang/Object;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
			handler.desc, "handler descriptor: (@Coerce Object node, CallbackInfoReturnable<Predicate> cir)");

		Map<String, Object> inject = handler.annotation("Lorg/spongepowered/asm/mixin/injection/Inject;");
		assertNotNull(inject, "the handler must carry @Inject");
		assertEquals(List.of("permissionCheck(Lxyz/eclipseisoffline/commonpermissionsapi/api/CommonPermissionNode;)"
			+ "Ljava/util/function/Predicate;"), inject.get("method"),
			"@Inject must target CustomNameCommands#permissionCheck(CommonPermissionNode) returning Predicate");
		assertEquals(Boolean.TRUE, inject.get("cancellable"), "@Inject must be cancellable so the predicate can be replaced");
		assertEquals(List.of(Map.of("value", "HEAD")), inject.get("at"), "@Inject must inject at HEAD");

		assertEquals(Set.of("0:Lorg/spongepowered/asm/mixin/injection/Coerce;"), handler.paramAnnotations,
			"the node parameter must be @Coerce, because CommonPermissionNode is not on the compile classpath");
	}

	// ── Pinned Custom Name jar (ASM) ──────────────────────────────────────────

	/** The reflective bridge names the classes the jar actually contains. */
	@Test
	void bridgeNamesMatchTheJarClasses() throws IOException {
		assertEquals(PERMISSIONS_CLASS.replace('/', '.').replace(".class", ""),
			CustomNameOtherPermission.PERMISSIONS_CLASS);
		assertEquals(COMMON_PERMISSIONS.replace('/', '.'), CustomNameOtherPermission.API_CLASS);
		assertEquals(NODE_TYPE.substring(1, NODE_TYPE.length() - 1).replace('/', '.'),
			CustomNameOtherPermission.NODE_CLASS);
		assertEquals("OTHER", CustomNameOtherPermission.OTHER_FIELD);
		assertEquals("require", CustomNameOtherPermission.REQUIRE_METHOD);

		ClassScan permissions = scan(PERMISSIONS_CLASS, false);
		FieldScan other = permissions.fields.get("OTHER");
		assertNotNull(other, "CustomNamePermissions must declare OTHER");
		assertEquals(NODE_TYPE, other.desc(), "OTHER must be a CommonPermissionNode");
		assertTrue((other.access() & Opcodes.ACC_STATIC) != 0 && (other.access() & Opcodes.ACC_PUBLIC) != 0,
			"OTHER must be a public static field");
	}

	/**
	 * Only {@code register} and {@code otherPlayerNameCommand} read {@code customname:other}, anywhere in the jar. The gate
	 * wraps {@code permissionCheck} for that node, so a new reader would need its own gate.
	 */
	@Test
	void theOtherNodeIsReadOnlyByRegisterAndOtherPlayerNameCommand() throws Exception {
		Set<String> readers = new TreeSet<>();
		for (ClassScan cls : scanCustomNameJar().values()) {
			for (MethodScan method : cls.methods.values()) {
				if (method.fieldReads.contains(PERMISSIONS_OWNER + ".OTHER")) {
					readers.add(cls.simpleName() + "." + method.name);
				}
			}
		}
		assertEquals(Set.of("CustomNameCommands.otherPlayerNameCommand", "CustomNameCommands.register"), readers,
			"the only readers of customname:other must be register and otherPlayerNameCommand");
	}

	/**
	 * Every caller of {@code PlayerNameManager.updatePlayerName} and {@code CustomNameUtil.clearPlayerName} in the jar is in
	 * this set. {@code lambda$updatePlayerName$0} picks the target from {@code other}; {@code lambda$menuOrClearPlayerName$0}
	 * clears the {@code other} player when {@code other} is true; {@code handleClearName} clears only the packet sender. A new
	 * caller could change another player's name without passing the {@code /name other} gate, so it must be reviewed here.
	 */
	@Test
	void everyCallerOfTheNameMutatorsIsKnown() throws Exception {
		Map<String, Set<String>> callers = new TreeMap<>();
		for (ClassScan cls : scanCustomNameJar().values()) {
			for (MethodScan method : cls.methods.values()) {
				for (String call : method.invocations) {
					if (call.startsWith(PLAYER_NAME_MANAGER + ".updatePlayerName ")) {
						callers.computeIfAbsent("PlayerNameManager.updatePlayerName", key -> new TreeSet<>())
							.add(cls.simpleName() + "." + method.name);
					}
					if (call.startsWith(CUSTOM_NAME_UTIL + ".clearPlayerName ")) {
						callers.computeIfAbsent("CustomNameUtil.clearPlayerName", key -> new TreeSet<>())
							.add(cls.simpleName() + "." + method.name);
					}
				}
			}
		}
		assertEquals(Map.of(
				"PlayerNameManager.updatePlayerName", Set.of("CustomNameCommands.lambda$updatePlayerName$0",
					"CustomNameUtil.clearPlayerName"),
				"CustomNameUtil.clearPlayerName", Set.of("CustomNameCommands.lambda$menuOrClearPlayerName$0",
					"ServerCommonPacketListenerImplMixin.lambda$handleClearName$1")),
			callers, "the callers of the name mutators changed; review each against the /name other gate");
	}

	/**
	 * The fix returns {@code CommonPermissions.require(node, GAMEMASTERS)}, so the original method must build that same
	 * predicate when {@code require_permissions} is true. Pins the branch the fix reuses.
	 */
	@Test
	void permissionCheckBuildsRequireGamemastersWhenRequirePermissionsIsTrue() throws IOException {
		ClassScan commands = scan(COMMANDS_CLASS, false);
		MethodScan check = commands.method(PERMISSION_CHECK, PERMISSION_CHECK_DESC);
		assertNotNull(check, "CustomNameCommands#permissionCheck(CommonPermissionNode) must exist");
		assertTrue((check.access & Opcodes.ACC_PRIVATE) != 0 && (check.access & Opcodes.ACC_STATIC) != 0,
			"permissionCheck must be private static");

		assertTrue(check.invocations.contains(CONFIG_CLASS + ".requirePermissions ()Z"),
			"permissionCheck must branch on CustomNameConfig#requirePermissions");
		assertTrue(check.invocations.contains(COMMON_PERMISSIONS + ".require " + REQUIRE_DESC),
			"permissionCheck must call CommonPermissions.require(node, PermissionLevel)");
		assertTrue(check.fieldReads.contains("net/minecraft/server/permissions/PermissionLevel.GAMEMASTERS"),
			"permissionCheck must pass PermissionLevel.GAMEMASTERS");
	}

	/**
	 * The {@code customname:other} node reaches {@code permissionCheck} from the {@code other} literal in {@code register}
	 * and from every {@code other <type>} literal in {@code otherPlayerNameCommand}. Those are the only call sites the
	 * injection has to cover.
	 */
	@Test
	void otherNodeIsCheckedThroughPermissionCheck() throws IOException {
		ClassScan commands = scan(COMMANDS_CLASS, false);
		String otherField = PERMISSIONS_CLASS.replace(".class", "") + ".OTHER";
		for (String methodName : List.of("register", "otherPlayerNameCommand")) {
			MethodScan method = commands.methodNamed(methodName);
			assertNotNull(method, methodName + " must exist");
			assertTrue(method.fieldReads.contains(otherField),
				methodName + " must read CustomNamePermissions.OTHER");
			assertTrue(method.invocations.contains(COMMANDS_OWNER + "." + PERMISSION_CHECK + " " + PERMISSION_CHECK_DESC),
				methodName + " must check OTHER through permissionCheck");
		}
	}

	/**
	 * {@code BYPASS_RESTRICTIONS} is read by direct {@code checkPermission} calls. The fix must not change it, so no method
	 * that reads it may route it through {@code permissionCheck}.
	 */
	@Test
	void bypassRestrictionsIsReadOnlyByTheGuardedUpdatePlayerNameLambda() throws Exception {
		String bypassField = PERMISSIONS_OWNER + ".BYPASS_RESTRICTIONS";
		Set<String> readers = new TreeSet<>();
		for (ClassScan cls : scanCustomNameJar().values()) {
			for (MethodScan method : cls.methods.values()) {
				if (!method.fieldReads.contains(bypassField)) {
					continue;
				}
				readers.add(cls.simpleName() + "." + method.name);
				assertTrue(method.invocations.contains(COMMANDS_OWNER + ".checkPermission " + CHECK_PERMISSION_DESC),
					method.name + " reads BYPASS_RESTRICTIONS and must check it with checkPermission");
				assertFalse(method.invocations.contains(COMMANDS_OWNER + "." + PERMISSION_CHECK + " " + PERMISSION_CHECK_DESC),
					method.name + " reads BYPASS_RESTRICTIONS and must not route it through permissionCheck");
				assertTrue(method.invocations.contains(CONFIG_CLASS + ".operatorsBypassRestrictions ()Z"),
					method.name + " must read operatorsBypassRestrictions() before it consults BYPASS_RESTRICTIONS");
			}
		}
		assertEquals(Set.of("CustomNameCommands.lambda$updatePlayerName$0"), readers,
			"BYPASS_RESTRICTIONS must be read only by the updatePlayerName lambda, which the fix leaves unchanged");
	}

	/** Reads every class in the pinned Custom Name jar, keyed by internal name. Nested Common Permissions API is not read. */
	private static Map<String, ClassScan> scanCustomNameJar() throws IOException {
		URL classUrl = CustomNamePermissionsMixinTest.class.getClassLoader().getResource(COMMANDS_CLASS);
		assertNotNull(classUrl, COMMANDS_CLASS + " must exist on the test classpath; testRuntimeOnly must carry the pinned jar");
		URL jarUrl = ((JarURLConnection) classUrl.openConnection()).getJarFileURL();
		try {
			Map<String, ClassScan> classes = new LinkedHashMap<>();
			try (JarFile jar = new JarFile(new File(jarUrl.toURI()))) {
				Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					String name = entry.getName();
					if (!name.endsWith(".class") || !name.startsWith(PACKAGE_PREFIX)) {
						continue;
					}
					try (InputStream input = jar.getInputStream(entry)) {
						ClassScan scan = new ClassScan();
						new ClassReader(input).accept(scan, 0);
						classes.put(scan.internalName, scan);
					}
				}
			}
			assertFalse(classes.isEmpty(), "the Custom Name jar must contain classes");
			return classes;
		} catch (URISyntaxException e) {
			throw new IOException("the Custom Name jar URL is not a file URL: " + jarUrl, e);
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private static String readMixinConfig() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** Reads a class from the test classpath. The mixin is in this repo; the Custom Name classes come from the pinned jar. */
	private static ClassScan scan(String resourceName, boolean mixin) throws IOException {
		try (InputStream input = resource(resourceName)) {
			assertNotNull(input, resourceName + " must exist on the test classpath"
				+ (mixin ? "" : "; testRuntimeOnly must carry the pinned Custom Name jar"));
			ClassScan scan = new ClassScan();
			new ClassReader(input).accept(scan, 0);
			return scan;
		}
	}

	private static InputStream resource(String name) {
		return CustomNamePermissionsMixinTest.class.getClassLoader().getResourceAsStream(name);
	}

	/** Annotation values keyed by name. Nested annotations and arrays become maps and lists. */
	private static final class Values extends AnnotationVisitor {
		private final Map<String, Object> values;

		Values(Map<String, Object> values) {
			super(Opcodes.ASM9);
			this.values = values;
		}

		@Override
		public void visit(String name, Object value) {
			values.put(name, value);
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			values.put(name, value);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			List<Object> list = new ArrayList<>();
			values.put(name, list);
			return new AnnotationVisitor(Opcodes.ASM9) {
				@Override
				public void visit(String ignored, Object value) {
					list.add(value);
				}

				@Override
				public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
					Map<String, Object> nested = new LinkedHashMap<>();
					list.add(nested);
					return new Values(nested);
				}
			};
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			Map<String, Object> nested = new LinkedHashMap<>();
			values.put(name, nested);
			return new Values(nested);
		}
	}

	private static final class ClassScan extends ClassVisitor {
		final Map<String, Map<String, Object>> classAnnotations = new LinkedHashMap<>();
		final Map<String, MethodScan> methods = new LinkedHashMap<>();
		final Map<String, FieldScan> fields = new LinkedHashMap<>();
		String internalName = "";

		ClassScan() {
			super(Opcodes.ASM9);
		}

		/** The class name without its package, as written in the expected-caller sets. */
		String simpleName() {
			return internalName.substring(internalName.lastIndexOf('/') + 1);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			internalName = name;
		}

		Map<String, Object> classAnnotation(String descriptor) {
			return classAnnotations.get(descriptor);
		}

		MethodScan method(String name, String descriptor) {
			return methods.get(name + descriptor);
		}

		MethodScan methodNamed(String name) {
			for (MethodScan method : methods.values()) {
				if (method.name.equals(name)) {
					return method;
				}
			}
			return null;
		}

		/** The one method this mixin injects into, found by its {@code @Inject} annotation. */
		MethodScan onlyInjector() {
			List<MethodScan> injectors = new ArrayList<>();
			for (MethodScan method : methods.values()) {
				if (method.annotation("Lorg/spongepowered/asm/mixin/injection/Inject;") != null) {
					injectors.add(method);
				}
			}
			assertEquals(1, injectors.size(), "the mixin must have exactly one @Inject");
			return injectors.get(0);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			Map<String, Object> values = new LinkedHashMap<>();
			classAnnotations.put(descriptor, values);
			return new Values(values);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			fields.put(name, new FieldScan(access, descriptor));
			return null;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodScan method = new MethodScan(access, name, descriptor);
			methods.put(name + descriptor, method);
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
					Map<String, Object> values = new LinkedHashMap<>();
					method.annotations.put(annotationDescriptor, values);
					return new Values(values);
				}

				@Override
				public AnnotationVisitor visitParameterAnnotation(int parameter, String annotationDescriptor, boolean visible) {
					method.paramAnnotations.add(parameter + ":" + annotationDescriptor);
					return null;
				}

				@Override
				public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
					if (opcode == Opcodes.GETSTATIC) {
						method.fieldReads.add(owner + "." + fieldName);
					}
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
						boolean isInterface) {
					method.invocations.add(owner + "." + methodName + " " + methodDescriptor);
				}
			};
		}
	}

	private static final class MethodScan {
		final int access;
		final String name;
		final String desc;
		final Map<String, Map<String, Object>> annotations = new LinkedHashMap<>();
		/** "index:descriptor" for each parameter annotation. */
		final Set<String> paramAnnotations = new TreeSet<>();
		/** "owner.name" for each GETSTATIC. */
		final Set<String> fieldReads = new TreeSet<>();
		/** "owner.name descriptor" for each invoke. */
		final Set<String> invocations = new TreeSet<>();

		MethodScan(int access, String name, String desc) {
			this.access = access;
			this.name = name;
			this.desc = desc;
		}

		Map<String, Object> annotation(String descriptor) {
			return annotations.get(descriptor);
		}
	}

	private record FieldScan(int access, String desc) {
	}
}
