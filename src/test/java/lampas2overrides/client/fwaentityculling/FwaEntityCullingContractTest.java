package lampas2overrides.client.fwaentityculling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lampas2overrides.fwaentityculling.FwaEntityCullingProfile;

/**
 * Contract tests for the Fancy World Animations 1.3.4 and EntityCulling 1.12.0 pair. Both jars are read with ASM from the
 * test runtime classpath and never loaded, so the vendor classes are not initialized.
 *
 * <p>The vendor side is what the compiled mixin must match. The EntityCulling descriptors come from its jar, and the fwa
 * descriptors from fwa's jar, so a change in either release fails here rather than at startup.
 */
class FwaEntityCullingContractTest {

	private static final String CULL_TASK = "dev/tr7zw/entityculling/CullTask";
	private static final String OCCLUSION_CULLING = "dev/tr7zw/entityculling/versionless/occlusionculling/OcclusionCulling";
	private static final String OCCLUSION_CULLING_DESC = "L" + OCCLUSION_CULLING + ";";
	private static final String VEC3D_DESC = "Ldev/tr7zw/entityculling/versionless/occlusionculling/util/Vec3d;";
	private static final String CULL_ENTITIES = "cullEntities";
	private static final String CULL_ENTITIES_DESC = "(Lnet/minecraft/world/phys/Vec3;" + VEC3D_DESC + ")V";

	private static final String OLD_INSTANCE = "com/logisticscraft/occlusionculling/OcclusionCullingInstance";
	private static final String OLD_INSTANCE_DESC = "L" + OLD_INSTANCE + ";";

	private static final String FWA_VENDOR_MIXIN = "fr/madu59/fwa/mixin/client/compat/entityculling/CullTaskMixin";

	private static final String MIXIN = "lampas2overrides/client/fwaentityculling/mixin/CullTaskAnimationCullingMixin";
	private static final String HANDLER = "lampas2$cullAnimations";
	private static final String MIXIN_CONFIG = "lampas2-overrides.fwaentityculling.mixins.json";
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";
	private static final String CANCELLER = "lampas2overrides.fwaentityculling.FwaCullTaskMixinCanceller";

	private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String SHADOW_ANNOTATION = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String INJECT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";

	@Test
	void entityCullingCullTaskHasTheRetypedMembers() throws IOException {
		ClassNode cullTask = read(CULL_TASK);
		FieldNode culling = field(cullTask, "culling");
		assertEquals(OCCLUSION_CULLING_DESC, culling.desc);
		assertEquals(0, culling.access & Opcodes.ACC_FINAL, "culling must stay writable for the mixin");
		FieldNode hitboxLimit = field(cullTask, "hitboxLimit");
		assertEquals("I", hitboxLimit.desc);
		assertTrue((hitboxLimit.access & Opcodes.ACC_FINAL) != 0, "hitboxLimit must be final");
		assertEquals(CULL_ENTITIES_DESC, method(cullTask, CULL_ENTITIES).desc);
	}

	@Test
	void fwaCullTaskMixinShadowsTheOldInstanceType() throws IOException {
		ClassNode vendor = read(FWA_VENDOR_MIXIN);
		assertEquals(FwaEntityCullingProfile.VENDOR_MIXIN, FWA_VENDOR_MIXIN.replace('/', '.'));
		assertEquals(OLD_INSTANCE_DESC, field(vendor, "culling").desc);
	}

	/**
	 * The compiled mixin must target CullTask, inject only into cullEntities at TAIL, take the vendor's arguments
	 * followed by CallbackInfo, and shadow the vendor's field types.
	 */
	@Test
	void compiledMixinMatchesTheVendorTypes() throws IOException {
		ClassNode cullTask = read(CULL_TASK);
		ClassNode mixin = read(MIXIN);

		AnnotationNode mixinAnnotation = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, MIXIN_ANNOTATION);
		assertNotNull(mixinAnnotation, mixin.name + " must carry @Mixin");
		assertEquals(List.of(Type.getObjectType(CULL_TASK)), targets(value(mixinAnnotation, "value")));

		List<String> injected = new ArrayList<>();
		for (MethodNode method : mixin.methods) {
			AnnotationNode inject = annotation(method.visibleAnnotations, method.invisibleAnnotations, INJECT_ANNOTATION);
			if (inject != null) {
				for (Object name : (List<?>) value(inject, "method")) {
					injected.add((String) name);
				}
			}
		}
		assertEquals(List.of(CULL_ENTITIES), injected, "the mixin must inject into cullEntities only");

		MethodNode handler = method(mixin, HANDLER);
		AnnotationNode inject = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, INJECT_ANNOTATION);
		assertNotNull(inject, HANDLER + " must carry @Inject");
		List<?> ats = (List<?>) value(inject, "at");
		assertNotNull(ats, HANDLER + " must set @At");
		assertEquals("TAIL", value((AnnotationNode) ats.get(0), "value"), HANDLER + " must inject at TAIL");

		Type[] vendorArgs = Type.getArgumentTypes(method(cullTask, CULL_ENTITIES).desc);
		Type[] handlerArgs = Arrays.copyOf(vendorArgs, vendorArgs.length + 1);
		handlerArgs[vendorArgs.length] = Type.getObjectType(CALLBACK_INFO);
		assertEquals(Type.getMethodDescriptor(Type.VOID_TYPE, handlerArgs), handler.desc,
			HANDLER + " must take the cullEntities arguments followed by CallbackInfo");

		for (String name : List.of("culling", "hitboxLimit")) {
			FieldNode shadow = field(mixin, name);
			assertNotNull(annotation(shadow.visibleAnnotations, shadow.invisibleAnnotations, SHADOW_ANNOTATION), name + " must be @Shadow");
			assertEquals(field(cullTask, name).desc, shadow.desc, "@Shadow " + name + " must match the vendor descriptor");
		}
	}

	@Test
	void mixinConfigNamesTheClientPluginAndTheAnimationMixin() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertEquals("lampas2overrides.client.fwaentityculling.FwaEntityCullingMixinPlugin", config.get("plugin").getAsString());
		assertEquals("lampas2overrides.client.fwaentityculling.mixin", config.get("package").getAsString());
		assertEquals(List.of("CullTaskAnimationCullingMixin"), strings(config.getAsJsonArray("client")));
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
		assertTrue(config.get("required").getAsBoolean());
	}

	@Test
	void fabricModJsonRegistersTheConfigAndTheCancellerEntrypoint() throws IOException {
		JsonObject metadata = readJson(FABRIC_MOD_JSON);

		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			JsonObject mixin = entry.getAsJsonObject();
			if (MIXIN_CONFIG.equals(mixin.get("config").getAsString())) {
				registered = mixin;
			}
		}
		assertNotNull(registered, "fabric.mod.json must register " + MIXIN_CONFIG);
		assertEquals("client", registered.get("environment").getAsString());

		assertEquals(List.of(CANCELLER), strings(metadata.getAsJsonObject("entrypoints").getAsJsonArray("mixinsquared")));

		JsonObject depends = metadata.getAsJsonObject("depends");
		assertFalse(depends.has("fwa"), "fwa must stay optional");
		assertFalse(depends.has("entityculling"), "entityculling must stay optional");
	}

	@Test
	void matchesAcceptsOnlyTheAuditedPair() {
		assertTrue(FwaEntityCullingProfile.matches("1.3.4", "1.12.0"));
		assertFalse(FwaEntityCullingProfile.matches("1.3.4", "1.11.3"));
		assertFalse(FwaEntityCullingProfile.matches("1.3.6", "1.12.0"));
		assertFalse(FwaEntityCullingProfile.matches(null, "1.12.0"));
		assertFalse(FwaEntityCullingProfile.matches("1.3.4", null));
	}

	private static List<?> targets(Object value) {
		return value instanceof List<?> list ? list : List.of(value);
	}

	private static List<String> strings(Iterable<JsonElement> array) {
		List<String> found = new ArrayList<>();
		for (JsonElement element : array) {
			found.add(element.getAsString());
		}
		return found;
	}

	private static FieldNode field(ClassNode owner, String name) {
		for (FieldNode field : owner.fields) {
			if (field.name.equals(name)) {
				return field;
			}
		}
		throw new AssertionError(owner.name + "." + name + " is not declared");
	}

	private static MethodNode method(ClassNode owner, String name) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name)) {
				return method;
			}
		}
		throw new AssertionError(owner.name + "." + name + " is not declared");
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

	private static ClassNode read(String internalName) throws IOException {
		try (InputStream input = FwaEntityCullingContractTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			assertNotNull(input, internalName + " must be on the test classpath");
			ClassNode node = new ClassNode();
			new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return node;
		}
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream in = FwaEntityCullingContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(in, "Missing " + resource + " on the test classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
