package lampas2overrides.wilderdelights;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract test for the Wilder Delights 1.0.0-26.2 creative tab icon fix.
 *
 * <p>The vendor facts are read with ASM from the pinned Wilder Delights jar on the test runtime classpath, without loading
 * it. The mixin facts are read the same way from the compiled mixin, because Mixin annotations are
 * {@code RetentionPolicy.CLASS}. The {@code @At} target is built from the vendor's GETSTATIC owner, name and descriptor, and
 * the redirect's method string must equal the vendor's icon method name and descriptor, so a change on either side fails
 * the test.
 *
 * <p>Wilder Wild is not on the test classpath. The handler's item id is checked against the {@code wilderwild:split_coconut}
 * string constant that Wilder Delights itself uses, not against Wilder Wild's registry.
 */
class WilderDelightsTabIconContractTest {

	private static final String JAR_SHA256 = "b21035bd8587761a7530300fb99cbe20057eeae7cb119d17c592d1f78f5ac543";
	private static final String VENDOR_CLASS = "com/lilithelder/wilderdelights/WilderDelights";
	private static final String MIXIN_CLASS = "lampas2overrides/wilderdelights/mixin/WilderDelightsTabIconMixin";
	private static final String MIXIN_CONFIG = "lampas2-overrides.wilderdelights.mixins.json";
	private static final String ICON_METHOD = "lambda$onInitialize$0";
	private static final String ICON_DESC = "()Lnet/minecraft/world/item/ItemStack;";
	private static final String WW_ITEMS = "net/frozenblock/wilderwild/registry/WWItems";
	private static final String SPLIT_COCONUT = "SPLIT_COCONUT";
	private static final String SPLIT_COCONUT_DESC = "Lnet/minecraft/world/item/Item;";
	private static final String HANDLER = "lampas2$splitCoconut";
	private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String REDIRECT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

	// ── Mixin config document ──────────────────────────────────────────────────

	@Test
	void mixinConfigExists() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
		}
	}

	@Test
	void mixinConfigReferencesPluginAndMixin() throws IOException {
		JsonObject config = readMixinConfig();
		assertEquals("lampas2overrides.wilderdelights.WilderDelightsMixinPlugin", config.get("plugin").getAsString(),
			"config must reference WilderDelightsMixinPlugin");
		assertEquals("lampas2overrides.wilderdelights.mixin", config.get("package").getAsString(),
			"config must name the mixin package");
		List<String> mixins = new ArrayList<>();
		config.getAsJsonArray("mixins").forEach(element -> mixins.add(element.getAsString()));
		assertEquals(List.of("WilderDelightsTabIconMixin"), mixins, "config must list exactly WilderDelightsTabIconMixin");
	}

	@Test
	void mixinConfigHasDefaultRequireOne() throws IOException {
		JsonObject config = readMixinConfig();
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt(),
			"config must set defaultRequire: 1 so a missing field read fails loudly on 1.0.0-26.2");
	}

	@Test
	void fabricModJsonRegistersTheConfigOnBothSides() throws IOException {
		JsonObject metadata;
		try (InputStream input = resource("fabric.mod.json")) {
			assertNotNull(input, "fabric.mod.json must exist on the classpath");
			metadata = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			JsonObject mixin = entry.getAsJsonObject();
			if (MIXIN_CONFIG.equals(mixin.get("config").getAsString())) {
				registered = mixin;
				break;
			}
		}
		assertNotNull(registered, "fabric.mod.json must register " + MIXIN_CONFIG);
		assertFalse(registered.has("environment"), "the target is the Wilder Delights main entrypoint class, loaded on both sides");
		assertTrue(metadata.getAsJsonObject("suggests").has("wilderdelights"), "fabric.mod.json must suggest wilderdelights");
		assertFalse(metadata.getAsJsonObject("depends").has("wilderdelights"), "Wilder Delights must stay optional");
	}

	// ── Version gate ───────────────────────────────────────────────────────────

	/**
	 * The gate must stay exactly "1.0.0-26.2". Change it only after inspecting a new Wilder Delights jar.
	 */
	@Test
	void pluginGatesOnExactVersion() {
		assertEquals("1.0.0-26.2", WilderDelightsMixinPlugin.AFFECTED_VERSION,
			"plugin must gate on exactly 1.0.0-26.2; update only after inspecting the new jar");
	}

	// ── Pinned vendor jar ──────────────────────────────────────────────────────

	@Test
	void jarIsTheInspectedOne() throws Exception {
		assertEquals(JAR_SHA256, sha256(Files.readAllBytes(wilderDelightsJar())),
			"Wilder Delights jar on the test classpath");
	}

	@Test
	void jarDeclaresTheGatedVersion() throws Exception {
		try (ZipFile jar = new ZipFile(wilderDelightsJar().toFile())) {
			ZipEntry entry = jar.getEntry("fabric.mod.json");
			assertNotNull(entry, "fabric.mod.json is not in the Wilder Delights jar");
			JsonObject metadata;
			try (InputStream input = jar.getInputStream(entry)) {
				metadata = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
			}
			assertEquals("wilderdelights", metadata.get("id").getAsString(), "mod id in the Wilder Delights jar");
			assertEquals(WilderDelightsMixinPlugin.AFFECTED_VERSION, metadata.get("version").getAsString(),
				"the gate compares the loader's version string, which the jar must declare");
		}
	}

	// ── Vendor bytecode ────────────────────────────────────────────────────────

	@Test
	void iconSupplierReadsSplitCoconutAsOneItemGetstatic() throws Exception {
		FieldInsnNode read = onlyGetstatic(method(readClass(VENDOR_CLASS), ICON_METHOD, ICON_DESC));
		assertEquals(WW_ITEMS, read.owner, "owner of the icon supplier's GETSTATIC");
		assertEquals(SPLIT_COCONUT, read.name, "name of the icon supplier's GETSTATIC");
		assertEquals(SPLIT_COCONUT_DESC, read.desc, "descriptor of the icon supplier's GETSTATIC");
	}

	// ── Compiled mixin structural contract (ASM) ───────────────────────────────

	@Test
	void mixinTargetsTheVendorClass() throws Exception {
		AnnotationNode mixin = annotation(readClass(MIXIN_CLASS), MIXIN_ANNOTATION);
		assertNotNull(mixin, "the mixin class must carry @Mixin");
		assertEquals(List.of(VENDOR_CLASS.replace('/', '.')), value(mixin, "targets"), "@Mixin targets");
		assertEquals(Boolean.FALSE, value(mixin, "remap"), "@Mixin must not remap");
	}

	@Test
	void redirectNamesTheVendorIconMethodAndItsGetstatic() throws Exception {
		FieldInsnNode vendorRead = onlyGetstatic(method(readClass(VENDOR_CLASS), ICON_METHOD, ICON_DESC));
		String vendorTarget = "L" + vendorRead.owner + ";" + vendorRead.name + ":" + vendorRead.desc;

		MethodNode handler = method(readClass(MIXIN_CLASS), HANDLER, "()" + vendorRead.desc);
		assertTrue((handler.access & Opcodes.ACC_STATIC) != 0, HANDLER + " must be static to redirect a static field read");
		AnnotationNode redirect = annotation(handler, REDIRECT_ANNOTATION);
		assertNotNull(redirect, HANDLER + " must carry @Redirect");
		assertEquals(List.of(ICON_METHOD + ICON_DESC), value(redirect, "method"), "@Redirect method");
		assertEquals(Boolean.FALSE, value(redirect, "remap"), "@Redirect must not remap");

		// Mixin 0.17 declares Redirect.at as At[], so the compiled element is a one-element array.
		List<?> ats = (List<?>) value(redirect, "at");
		assertNotNull(ats, "@Redirect must carry @At");
		assertEquals(1, ats.size(), "@Redirect must carry exactly one @At");
		AnnotationNode at = (AnnotationNode) ats.get(0);
		assertEquals("FIELD", value(at, "value"), "@At value");
		assertEquals(vendorTarget, value(at, "target"), "@At target must be the vendor's GETSTATIC member");
		assertEquals(Integer.valueOf(Opcodes.GETSTATIC), value(at, "opcode"), "@At opcode must be GETSTATIC");
	}

	/**
	 * Wilder Delights names the split coconut by string elsewhere in {@code onInitialize}; the handler's
	 * {@code Identifier.fromNamespaceAndPath} arguments must join to one of the vendor's own string constants.
	 */
	@Test
	void handlerLooksUpAnItemIdTheVendorUses() throws Exception {
		ClassNode vendor = readClass(VENDOR_CLASS);
		List<String> vendorStrings = new ArrayList<>();
		for (MethodNode method : vendor.methods) {
			vendorStrings.addAll(stringConstants(method));
		}
		assertTrue(vendorStrings.contains("wilderwild:split_coconut"),
			"Wilder Delights must still name wilderwild:split_coconut");

		FieldInsnNode vendorRead = onlyGetstatic(method(vendor, ICON_METHOD, ICON_DESC));
		List<String> handlerStrings = stringConstants(method(readClass(MIXIN_CLASS), HANDLER, "()" + vendorRead.desc));
		assertEquals(2, handlerStrings.size(), HANDLER + " string constants (namespace, path)");
		assertTrue(vendorStrings.contains(handlerStrings.get(0) + ":" + handlerStrings.get(1)),
			HANDLER + " must look up an item id Wilder Delights itself uses, found " + handlerStrings);
	}

	// ── Helpers ────────────────────────────────────────────────────────────────

	private static FieldInsnNode onlyGetstatic(MethodNode method) {
		List<FieldInsnNode> reads = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
				reads.add(field);
			}
		}
		assertEquals(1, reads.size(), method.name + " GETSTATIC count");
		return reads.get(0);
	}

	private static List<String> stringConstants(MethodNode method) {
		List<String> strings = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String string) {
				strings.add(string);
			}
		}
		return strings;
	}

	private static MethodNode method(ClassNode owner, String name, String descriptor) {
		List<MethodNode> found = owner.methods.stream()
			.filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
			.toList();
		assertEquals(1, found.size(), owner.name + "." + name + descriptor);
		return found.get(0);
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

	private static AnnotationNode annotation(ClassNode owner, String descriptor) {
		AnnotationNode found = find(owner.visibleAnnotations, descriptor);
		return found != null ? found : find(owner.invisibleAnnotations, descriptor);
	}

	private static AnnotationNode annotation(MethodNode owner, String descriptor) {
		AnnotationNode found = find(owner.visibleAnnotations, descriptor);
		return found != null ? found : find(owner.invisibleAnnotations, descriptor);
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

	private static ClassNode readClass(String internalName) throws IOException {
		try (InputStream input = resource(internalName + ".class")) {
			assertNotNull(input, internalName + " must be on the test classpath");
			ClassNode node = new ClassNode();
			new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return node;
		}
	}

	private static Path wilderDelightsJar() throws Exception {
		URL url = WilderDelightsTabIconContractTest.class.getClassLoader().getResource(VENDOR_CLASS + ".class");
		assertNotNull(url, "Wilder Delights is not on the test classpath");
		return Path.of(((JarURLConnection) url.openConnection()).getJarFileURL().toURI());
	}

	private static JsonObject readMixinConfig() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static InputStream resource(String name) {
		return WilderDelightsTabIconContractTest.class.getClassLoader().getResourceAsStream(name);
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
