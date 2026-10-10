package lampas2overrides.illagerblabber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class IllagerBlabberMixinContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.illagerblabber.mixins.json";
	private static final String MIXIN_PACKAGE = "lampas2overrides/illagerblabber/mixin/";
	private static final String ABSTRACT_ILLAGER = "Lnet/minecraft/world/entity/monster/illager/AbstractIllager;";
	private static final String ILLAGER_TYPE = "Lcom/leclowndu93150/illagerblabber/stuff/voice/IllagerType;";
	private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	private static final String LOGGER = "Lorg/slf4j/Logger;";
	private static final String UPDATE_ILLAGER = "updateIllager(" + ABSTRACT_ILLAGER + ILLAGER_TYPE + ")V";
	private static final String UPDATE_STATE = "updateIllagerState(" + ABSTRACT_ILLAGER + ")V";
	private static final String CONSTRUCTOR = "<init>(" + ABSTRACT_ILLAGER + ILLAGER_TYPE + ")V";
	private static final String INFO_STRING = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V";
	private static final String INFO_STRING_ARRAY = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V";
	private static final String INFO_STRING_OBJECT = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V";

	@Test
	void registersTheFeatureAsCommonAndGatedByExactArtifact() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("JAVA_25", config.get("compatibilityLevel").getAsString());
		assertEquals("lampas2overrides.illagerblabber.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.illagerblabber.IllagerBlabberMixinPlugin", config.get("plugin").getAsString());
		assertEquals(Set.of("IllagerVoiceRegistryAccessor", "IllagerVoiceManagerAccessor", "IllagerVoiceRegistryMixin",
			"IllagerVoiceManagerMixin"),
			stringSet(config.getAsJsonArray("mixins")));
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
		assertFalse(config.has("client"));
		assertFalse(config.has("server"));
		assertFalse(config.has("environment"));

		JsonObject metadata = readJson("fabric.mod.json");
		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			JsonObject mixin = entry.getAsJsonObject();
			if (MIXIN_CONFIG.equals(mixin.get("config").getAsString())) {
				registered = mixin;
				break;
			}
		}
		assertNotNull(registered, "fabric.mod.json must register the IllagerBlabber config");
		assertFalse(registered.has("environment"), "the registry fix must run on dedicated and integrated servers");

		assertTrue(stringSet(metadata.getAsJsonObject("entrypoints").getAsJsonArray("main"))
			.contains("lampas2overrides.illagerblabber.IllagerBlabberFixes"));
		assertFalse(stringSet(metadata.getAsJsonObject("entrypoints").getAsJsonArray("client"))
			.contains("lampas2overrides.illagerblabber.IllagerBlabberFixes"));
		JsonObject suggests = metadata.getAsJsonObject("suggests");
		assertTrue(suggests.has("illagerblabber"));
		assertFalse(metadata.getAsJsonObject("depends").has("illagerblabber"));
		assertTrue(metadata.get("description").getAsString().contains("IllagerBlabber"));
	}

	@Test
	void pseudoMixinsTargetOnlyTheExactVendorClassesWithoutRemapping() throws IOException {
		ClassSummary registry = summarize(MIXIN_PACKAGE + "IllagerVoiceRegistryMixin.class");
		assertPseudoTarget(registry, IllagerBlabberProfile.REGISTRY_TARGET);

		ClassSummary manager = summarize(MIXIN_PACKAGE + "IllagerVoiceManagerMixin.class");
		assertPseudoTarget(manager, IllagerBlabberProfile.MANAGER_TARGET);

		ClassSummary accessor = summarize(MIXIN_PACKAGE + "IllagerVoiceManagerAccessor.class");
		assertPseudoTarget(accessor, IllagerBlabberProfile.MANAGER_TARGET);
		assertTrue((accessor.access & Opcodes.ACC_INTERFACE) != 0, "the manager accessor must be an interface mixin");

		ClassSummary registryAccessor = summarize(MIXIN_PACKAGE + "IllagerVoiceRegistryAccessor.class");
		assertPseudoTarget(registryAccessor, IllagerBlabberProfile.REGISTRY_TARGET);
		assertTrue((registryAccessor.access & Opcodes.ACC_INTERFACE) != 0, "the registry accessor must be an interface mixin");
	}

	@Test
	void registryAccessorExposesEveryVendorMapAsAStaticGetter() throws IOException {
		ClassSummary accessor = summarize(MIXIN_PACKAGE + "IllagerVoiceRegistryAccessor.class");
		List<String> expected = List.of("voiceManagers", "hadTargetLastTick", "victoryTimers", "combatDebounceTimers",
			"lastProcessedTick", "lastPillagerTargets", "lastVindicatorTargets", "lastEvokerTargets",
			"lastGroupSpottedSoundTime");
		assertEquals(expected.size(), accessor.methods.size());
		for (String field : expected) {
			MethodSummary getter = accessor.method("lampas2$" + field, "()Ljava/util/concurrent/ConcurrentHashMap;");
			assertStatic(getter, true);
			Map<String, Object> annotation = getter.annotation("Lorg/spongepowered/asm/mixin/gen/Accessor;");
			assertNotNull(annotation, field + " must be an @Accessor");
			assertEquals(field, annotation.get("value"));
		}
	}

	@Test
	void registryTakeoverInjectsAtHeadAndCancelsTheVendorUpdate() throws IOException {
		ClassSummary registry = summarize(MIXIN_PACKAGE + "IllagerVoiceRegistryMixin.class");

		MethodSummary handler = registry.method("lampas2$updateWithoutRegistryLeak",
			"(" + ABSTRACT_ILLAGER + ILLAGER_TYPE + CALLBACK_INFO + ")V");
		assertStatic(handler, true);
		assertPrivate(handler, true);
		Map<String, Object> inject = handler.annotation("Lorg/spongepowered/asm/mixin/injection/Inject;");
		assertNotNull(inject, "the takeover must be an @Inject");
		assertEquals(List.of(UPDATE_ILLAGER), inject.get("method"));
		assertEquals("HEAD", atValue(inject));
		assertEquals(Boolean.TRUE, inject.get("cancellable"));
	}

	@Test
	void registryShadowsTheVendorStateUpdateAndDemotesItsInfoLog() throws IOException {
		ClassSummary registry = summarize(MIXIN_PACKAGE + "IllagerVoiceRegistryMixin.class");

		MethodSummary shadow = registry.method("updateIllagerState", "(" + ABSTRACT_ILLAGER + ")V");
		assertStatic(shadow, true);
		assertPrivate(shadow, true);
		assertNotNull(shadow.annotation("Lorg/spongepowered/asm/mixin/Shadow;"));

		MethodSummary redirect = registry.method("lampas2$demoteStateLog", "(" + LOGGER + "Ljava/lang/String;)V");
		assertStatic(redirect, true);
		Map<String, Object> annotation = redirect.annotation("Lorg/spongepowered/asm/mixin/injection/Redirect;");
		assertNotNull(annotation, "the state log must be a @Redirect");
		assertEquals(List.of(UPDATE_STATE), annotation.get("method"));
		assertEquals("INVOKE", atField(annotation, "value"));
		assertEquals(INFO_STRING, atField(annotation, "target"));
	}

	@Test
	void managerRedirectsDemoteConstructionAndVictoryLogsToDebug() throws IOException {
		ClassSummary manager = summarize(MIXIN_PACKAGE + "IllagerVoiceManagerMixin.class");

		MethodSummary construction = manager.method("lampas2$demoteConstructionLog",
			"(" + LOGGER + "Ljava/lang/String;[Ljava/lang/Object;)V");
		assertStatic(construction, true);
		Map<String, Object> constructionRedirect = construction.annotation("Lorg/spongepowered/asm/mixin/injection/Redirect;");
		assertNotNull(constructionRedirect);
		assertEquals(List.of(CONSTRUCTOR), constructionRedirect.get("method"));
		assertEquals("INVOKE", atField(constructionRedirect, "value"));
		assertEquals(INFO_STRING_ARRAY, atField(constructionRedirect, "target"));

		MethodSummary victory = manager.method("lampas2$demoteVictoryLog",
			"(" + LOGGER + "Ljava/lang/String;Ljava/lang/Object;)V");
		assertStatic(victory, true);
		Map<String, Object> victoryRedirect = victory.annotation("Lorg/spongepowered/asm/mixin/injection/Redirect;");
		assertNotNull(victoryRedirect);
		assertEquals(List.of("update()V"), victoryRedirect.get("method"));
		assertEquals("INVOKE", atField(victoryRedirect, "value"));
		assertEquals(INFO_STRING_OBJECT, atField(victoryRedirect, "target"));
	}

	@Test
	void managerAccessorReadsTheIllagerFieldAndNothingElse() throws IOException {
		ClassSummary accessor = summarize(MIXIN_PACKAGE + "IllagerVoiceManagerAccessor.class");
		assertEquals(1, accessor.methods.size(), "the accessor interface must expose only the illager field");
		MethodSummary getter = accessor.methods.get(0);
		assertEquals("getIllager", getter.name);
		assertEquals("()" + ABSTRACT_ILLAGER, getter.descriptor);
		Map<String, Object> annotation = getter.annotation("Lorg/spongepowered/asm/mixin/gen/Accessor;");
		assertNotNull(annotation);
		assertEquals("illager", annotation.get("value"));
	}

	@Test
	void gatePluginProfileAndEntrypointDoNotLinkTheVendorOrItsMixins() throws IOException {
		assertNoVendorLink("lampas2overrides/illagerblabber/IllagerBlabberMixinPlugin.class", false);
		assertNoVendorLink("lampas2overrides/illagerblabber/IllagerBlabberProfile.class", false);
		assertNoVendorLink("lampas2overrides/illagerblabber/IllagerBlabberFixes.class", false);
		// The cleanup adapter may use the instance accessor, but never a vendor class name.
		assertNoVendorLink("lampas2overrides/illagerblabber/IllagerBlabberRegistryCleanup.class", true);
	}

	@Test
	void compatibilityManifestRecordsTheExactArtifactAndRuntimeLimits() throws IOException {
		JsonObject target = readJson("compatibility-targets.json").getAsJsonObject("targets").getAsJsonObject("illagerblabber");
		assertEquals("IllagerBlabber", target.get("name").getAsString());
		assertEquals("common_registry_leak_mixin", target.get("type").getAsString());
		JsonObject profile = target.getAsJsonObject("versions").getAsJsonObject(IllagerBlabberProfile.VERSION);
		assertNotNull(profile, "missing IllagerBlabber 1.1.0 profile");
		assertEquals("patched", profile.get("status").getAsString());
		assertEquals(IllagerBlabberProfile.JAR_SHA256, profile.get("artifact_sha256").getAsString());
		assertEquals("docs/evidence/illagerblabber-registry.json", profile.get("probe_evidence").getAsString());
		String limit = profile.get("verification_limit").getAsString().toLowerCase(Locale.ROOT);
		assertTrue(limit.contains("dedicated-server runtime"));
		assertTrue(limit.contains("audible voice lines"));
		assertTrue(limit.contains("long-uptime heap"));
	}

	private static void assertPseudoTarget(ClassSummary summary, String expectedTarget) {
		assertTrue(summary.annotations.containsKey("Lorg/spongepowered/asm/mixin/Pseudo;"),
			"@Pseudo is required so an absent optional target skips cleanly");
		Map<String, Object> mixin = summary.annotations.get("Lorg/spongepowered/asm/mixin/Mixin;");
		assertNotNull(mixin, "class must carry @Mixin");
		assertEquals(List.of(expectedTarget), mixin.get("targets"));
		assertEquals(Boolean.FALSE, mixin.get("remap"), "external target mixins must declare remap = false");
	}

	private static void assertStatic(MethodSummary method, boolean expected) {
		assertEquals(expected, (method.access & Opcodes.ACC_STATIC) != 0, method.name + " static");
	}

	private static void assertPrivate(MethodSummary method, boolean expected) {
		assertEquals(expected, (method.access & Opcodes.ACC_PRIVATE) != 0, method.name + " private");
	}

	private static String atValue(Map<String, Object> annotation) {
		return atField(annotation, "value");
	}

	@SuppressWarnings("unchecked")
	private static String atField(Map<String, Object> annotation, String field) {
		Object value = annotation.get("at");
		assertNotNull(value, "annotation must declare an @At");
		// @Inject declares At[], @Redirect a single At.
		if (value instanceof List<?> list) {
			assertEquals(1, list.size(), "exactly one @At is expected");
			value = list.get(0);
		}
		Map<String, Object> at = (Map<String, Object>) value;
		return (String) at.get(field);
	}

	private static void assertNoVendorLink(String resource, boolean mayUseAccessor) throws IOException {
		try (InputStream input = resource(resource)) {
			assertNotNull(input, "missing class " + resource);
			String constantPool = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
			assertFalse(constantPool.contains("com/leclowndu93150"), resource + " must not link IllagerBlabber classes");
			if (!mayUseAccessor) {
				assertFalse(constantPool.contains("lampas2overrides/illagerblabber/mixin/"),
					resource + " must not reference mixin classes directly");
			}
		}
	}

	private static ClassSummary summarize(String resourceName) throws IOException {
		try (InputStream input = resource(resourceName)) {
			assertNotNull(input, "missing mixin class " + resourceName);
			ClassSummary summary = new ClassSummary();
			new ClassReader(input).accept(new SummaryVisitor(summary), 0);
			return summary;
		}
	}

	private static JsonObject readJson(String resourceName) throws IOException {
		try (InputStream input = resource(resourceName)) {
			assertNotNull(input, "missing resource " + resourceName);
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static Set<String> stringSet(JsonArray array) {
		Set<String> values = new HashSet<>();
		for (JsonElement value : array) {
			values.add(value.getAsString());
		}
		return values;
	}

	private static InputStream resource(String name) {
		return IllagerBlabberMixinContractTest.class.getClassLoader().getResourceAsStream(name);
	}

	private static final class ClassSummary {
		int access;
		final Map<String, Map<String, Object>> annotations = new LinkedHashMap<>();
		final List<MethodSummary> methods = new ArrayList<>();

		MethodSummary method(String name, String descriptor) {
			for (MethodSummary method : methods) {
				if (method.name.equals(name) && method.descriptor.equals(descriptor)) {
					return method;
				}
			}
			throw new AssertionError("missing method " + name + descriptor);
		}
	}

	private static final class MethodSummary {
		final int access;
		final String name;
		final String descriptor;
		final Map<String, Map<String, Object>> annotations = new LinkedHashMap<>();

		MethodSummary(int access, String name, String descriptor) {
			this.access = access;
			this.name = name;
			this.descriptor = descriptor;
		}

		Map<String, Object> annotation(String annotationDescriptor) {
			return annotations.get(annotationDescriptor);
		}
	}

	private static final class SummaryVisitor extends ClassVisitor {
		private final ClassSummary summary;

		SummaryVisitor(ClassSummary summary) {
			super(Opcodes.ASM9);
			this.summary = summary;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			summary.access = access;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			Map<String, Object> values = new LinkedHashMap<>();
			summary.annotations.put(descriptor, values);
			return new ValueRecorder(values);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodSummary method = new MethodSummary(access, name, descriptor);
			summary.methods.add(method);
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
					Map<String, Object> values = new LinkedHashMap<>();
					method.annotations.put(annotationDescriptor, values);
					return new ValueRecorder(values);
				}
			};
		}
	}

	private static final class ValueRecorder extends AnnotationVisitor {
		private final Map<String, Object> values;

		ValueRecorder(Map<String, Object> values) {
			super(Opcodes.ASM9);
			this.values = values;
		}

		@Override
		public void visit(String name, Object value) {
			values.put(name, value);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			List<Object> list = new ArrayList<>();
			values.put(name, list);
			return new ArrayRecorder(list);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			Map<String, Object> nested = new LinkedHashMap<>();
			values.put(name, nested);
			return new ValueRecorder(nested);
		}
	}

	private static final class ArrayRecorder extends AnnotationVisitor {
		private final List<Object> values;

		ArrayRecorder(List<Object> values) {
			super(Opcodes.ASM9);
			this.values = values;
		}

		@Override
		public void visit(String name, Object value) {
			values.add(value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			Map<String, Object> nested = new LinkedHashMap<>();
			values.add(nested);
			return new ValueRecorder(nested);
		}
	}
}
