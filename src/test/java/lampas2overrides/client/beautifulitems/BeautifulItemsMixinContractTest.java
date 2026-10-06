package lampas2overrides.client.beautifulitems;

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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class BeautifulItemsMixinContractTest {

	private static final String INITIALIZE_DESCRIPTOR =
		"initialize(Ljava/util/Set;Lnet/fabricmc/fabric/api/client/model/loading/v1/ModelLoadingPlugin$Context;)V";
	private static final String CALLBACK_DESCRIPTOR =
		"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
	private static final String MIXIN_CONFIG = "lampas2-overrides.beautifulitems.mixins.json";

	@Test
	void registersTheFeatureAsClientOnlyAndOptional() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("JAVA_25", config.get("compatibilityLevel").getAsString());
		assertEquals("lampas2overrides.client.beautifulitems.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.client.beautifulitems.BeautifulItemsMixinPlugin",
			config.get("plugin").getAsString());
		assertEquals(Set.of("BeautifulEnchantedBooksFabricMixin", "BeautifulPotionsFabricMixin"),
			stringSet(config.getAsJsonArray("client")));
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

		JsonObject metadata = readJson("fabric.mod.json");
		JsonObject registeredConfig = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			JsonObject mixin = entry.getAsJsonObject();
			if (MIXIN_CONFIG.equals(mixin.get("config").getAsString())) {
				registeredConfig = mixin;
				break;
			}
		}
		assertNotNull(registeredConfig, "fabric.mod.json must register the beautiful-items config");
		assertEquals("client", registeredConfig.get("environment").getAsString());
		JsonObject suggests = metadata.getAsJsonObject("suggests");
		assertTrue(suggests.has("beb"));
		assertTrue(suggests.has("beautiful_potions"));
		assertFalse(metadata.getAsJsonObject("depends").has("beb"));
		assertFalse(metadata.getAsJsonObject("depends").has("beautiful_potions"));
	}

	@Test
	void eachMixinClearsItsOwnStaticMapAtTheExactInitializerHead() throws IOException {
		assertMixinContract(
			"lampas2overrides/client/beautifulitems/mixin/BeautifulEnchantedBooksFabricMixin.class",
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET);
		assertMixinContract(
			"lampas2overrides/client/beautifulitems/mixin/BeautifulPotionsFabricMixin.class",
			BeautifulItemsProfiles.POTIONS_TARGET);
	}

	@Test
	void gatePluginAndProfilesDoNotLinkMinecraftClientModelTypes() throws IOException {
		assertNoClientMinecraftType("lampas2overrides/client/beautifulitems/BeautifulItemsMixinPlugin.class");
		assertNoClientMinecraftType("lampas2overrides/client/beautifulitems/BeautifulItemsProfiles.class");
	}

	@Test
	void compatibilityManifestRecordsBothExactArtifactsAndRuntimeLimits() throws IOException {
		JsonObject targets = readJson("compatibility-targets.json").getAsJsonObject("targets");
		assertManifestTarget(
			targets, "beb", "6.0.0", BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			"08819eb4b0773d389b850dad7cc0c2134a1fcb5d3693d28e03cc8c309181fa8f");
		assertManifestTarget(
			targets, "beautiful_potions", "2.0.1", BeautifulItemsProfiles.POTIONS_TARGET,
			"53d132f68b9c97105699e3de3542ad9f3ef5ec4e4df5e147f00e5f8510e33a82");
	}

	private static void assertMixinContract(String resourceName, String expectedTarget) throws IOException {
		MixinContract contract = readMixin(resourceName);
		assertTrue(contract.pseudo, "@Pseudo is required so an absent optional target can be skipped");
		assertEquals(List.of(expectedTarget), contract.targets);
		assertFalse(contract.remap, "external target mixins must declare remap = false");
		assertEquals("Ljava/util/Map;", contract.shadowDescriptor);
		assertTrue(contract.shadowStatic, "REGISTERED_MODELS must resolve as a static target field");
		assertTrue(contract.shadowAnnotated, "REGISTERED_MODELS must be a shadow");
		assertTrue(contract.shadowFinalAnnotated, "REGISTERED_MODELS is final in the vendor class");
		assertFalse(contract.hasClassInitializer,
			"the shadow must not emit a clinit that can overwrite the vendor's map");
		assertNotNull(contract.inject, "mixin must declare its reload injection");
		assertEquals(CALLBACK_DESCRIPTOR, contract.inject.handlerDescriptor,
			"the handler must remain callback-only and avoid optional/vendor method types");
		assertEquals(List.of(INITIALIZE_DESCRIPTOR), contract.inject.targetMethods);
		assertEquals("HEAD", contract.inject.atValue);
		assertTrue(contract.inject.clearsMap, "handler must clear REGISTERED_MODELS");
	}

	private static void assertManifestTarget(
		JsonObject targets,
		String modId,
		String version,
		String targetClass,
		String sha256
	) {
		JsonObject target = targets.getAsJsonObject(modId);
		assertNotNull(target, "missing compatibility target " + modId);
		assertEquals("client_resource_reload_mixin", target.get("type").getAsString());
		JsonObject profile = target.getAsJsonObject("versions").getAsJsonObject(version);
		assertNotNull(profile, "missing version profile " + modId + " " + version);
		assertEquals("patched", profile.get("status").getAsString());
		assertEquals(sha256, profile.get("artifact_sha256").getAsString());
		assertEquals("docs/evidence/beautiful-items-reload.json", profile.get("probe_evidence").getAsString());
		assertTrue(profile.get("verification_limit").getAsString().contains("full-pack behavior"));
		assertTrue(profile.get("verification_limit").getAsString().contains("visible item rendering"));
		assertTrue(BeautifulItemsProfiles.matchesProfile(targetClass, modId, version, sha256));
	}

	private static MixinContract readMixin(String resourceName) throws IOException {
		try (InputStream input = resource(resourceName)) {
			assertNotNull(input, "missing mixin class " + resourceName);
			MixinContract contract = new MixinContract();
			new ClassReader(input).accept(new MixinVisitor(contract), 0);
			return contract;
		}
	}

	private static JsonObject readJson(String resourceName) throws IOException {
		try (InputStream input = resource(resourceName)) {
			assertNotNull(input, "missing resource " + resourceName);
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static void assertNoClientMinecraftType(String resourceName) throws IOException {
		try (InputStream input = resource(resourceName)) {
			assertNotNull(input, "missing class " + resourceName);
			String constantPoolText = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
			assertFalse(constantPoolText.contains("net/minecraft/client/"),
				resourceName + " must not link Minecraft client types");
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
		return BeautifulItemsMixinContractTest.class.getClassLoader().getResourceAsStream(name);
	}

	private static final class MixinContract {
		boolean pseudo;
		boolean remap = true;
		final List<String> targets = new ArrayList<>();
		String shadowDescriptor;
		boolean shadowStatic;
		boolean shadowAnnotated;
		boolean shadowFinalAnnotated;
		boolean hasClassInitializer;
		InjectionContract inject;
	}

	private static final class InjectionContract {
		final List<String> targetMethods = new ArrayList<>();
		String handlerDescriptor;
		String atValue;
		boolean clearsMap;
	}

	private static final class MixinVisitor extends ClassVisitor {
		private final MixinContract contract;

		MixinVisitor(MixinContract contract) {
			super(Opcodes.ASM9);
			this.contract = contract;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if ("Lorg/spongepowered/asm/mixin/Pseudo;".equals(descriptor)) {
				contract.pseudo = true;
			}
			if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor)) {
				return super.visitAnnotation(descriptor, visible);
			}
			return new AnnotationVisitor(Opcodes.ASM9) {
				@Override
				public void visit(String name, Object value) {
					if ("remap".equals(name) && value instanceof Boolean remap) {
						contract.remap = remap;
					}
					if ("targets".equals(name) && value instanceof String target) {
						contract.targets.add(target);
					}
				}

				@Override
				public AnnotationVisitor visitArray(String name) {
					if (!"targets".equals(name)) {
						return super.visitArray(name);
					}
					return new AnnotationVisitor(Opcodes.ASM9) {
						@Override
						public void visit(String n, Object value) {
							contract.targets.add((String) value);
						}
					};
				}
			};
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			if (!"REGISTERED_MODELS".equals(name)) {
				return super.visitField(access, name, descriptor, signature, value);
			}
			contract.shadowDescriptor = descriptor;
			contract.shadowStatic = (access & Opcodes.ACC_STATIC) != 0;
			return new FieldVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
					if ("Lorg/spongepowered/asm/mixin/Shadow;".equals(descriptor)) {
						contract.shadowAnnotated = true;
					}
					if ("Lorg/spongepowered/asm/mixin/Final;".equals(descriptor)) {
						contract.shadowFinalAnnotated = true;
					}
					return super.visitAnnotation(descriptor, visible);
				}
			};
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if ("<clinit>".equals(name)) {
				contract.hasClassInitializer = true;
			}
			MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
			return new MethodVisitor(Opcodes.ASM9, base) {
				private InjectionContract currentInjection;

				@Override
				public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
					if (!"Lorg/spongepowered/asm/mixin/injection/Inject;".equals(annotationDescriptor)) {
						return super.visitAnnotation(annotationDescriptor, visible);
					}
					currentInjection = new InjectionContract();
					currentInjection.handlerDescriptor = descriptor;
					contract.inject = currentInjection;
					return new AnnotationVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitArray(String name) {
							if ("method".equals(name)) {
								return new AnnotationVisitor(Opcodes.ASM9) {
									@Override
									public void visit(String n, Object value) {
										currentInjection.targetMethods.add((String) value);
									}
								};
							}
							if ("at".equals(name)) {
								return new AnnotationVisitor(Opcodes.ASM9) {
									@Override
									public AnnotationVisitor visitAnnotation(String n, String nestedDescriptor) {
										return new AnnotationVisitor(Opcodes.ASM9) {
										@Override
										public void visit(String elementName, Object value) {
											if ("value".equals(elementName)) {
												currentInjection.atValue = (String) value;
											}
										}
									};
									}
								};
							}
							return super.visitArray(name);
						}
					};
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
					if (currentInjection != null && "java/util/Map".equals(owner)
						&& "clear".equals(methodName) && "()V".equals(methodDescriptor)) {
						currentInjection.clearsMap = true;
					}
				super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
				}
			};
		}
	}
}
