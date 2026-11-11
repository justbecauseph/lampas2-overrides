package lampas2overrides.client.statuseffectbars;

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

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for the Status Effect Bars entry points that the lampas2 mixins name by string, and for the vanilla
 * {@code Hud#extractEffects} blit order that {@code EffectIconPosition} depends on.
 *
 * <p>Reads the classes with ASM from the test runtime classpath, where Status Effect Bars 1.0.12 is {@code testRuntimeOnly}
 * and is never loaded. The runtime feature is not version-gated; these tests pin the release the pack runs and the
 * descriptors the mixins name.
 */
class StatusEffectBarsContractTest {

	private static final String RENDERER = "io/github/a5b84/statuseffectbars/StatusEffectBarRenderer";
	private static final String RENDER = "render";
	private static final String RENDER_DESC = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;"
		+ "Lnet/minecraft/world/effect/MobEffectInstance;IIIILio/github/a5b84/statuseffectbars/config/StatusEffectBarsConfig$LayoutConfig;)V";
	private static final String MIXIN = "lampas2overrides/client/statuseffectbars/mixin/StatusEffectBarRendererMixin";
	private static final String MODIFY_VARIABLE_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";

	private static final String HUD_MIXIN = "io/github/a5b84/statuseffectbars/mixin/HudMixin";
	private static final String ON_EXTRACT_EFFECTS = "onExtractEffects";
	private static final String HUD_TARGET =
		"Lnet/minecraft/client/gui/Hud;getMobEffectSprite(Lnet/minecraft/core/Holder;)Lnet/minecraft/resources/Identifier;";
	private static final String INJECT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String AT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/At;";

	private static final String HUD = "net/minecraft/client/gui/Hud";
	private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor";
	private static final String EXTRACT_EFFECTS = "extractEffects";
	private static final String EXTRACT_EFFECTS_DESC =
		"(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";
	private static final String BLIT_SPRITE = "blitSprite";
	private static final String BLIT_SPRITE_DESC =
		"(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";
	private static final String GET_MOB_EFFECT_SPRITE = "getMobEffectSprite";
	private static final String GET_MOB_EFFECT_SPRITE_DESC = "(Lnet/minecraft/core/Holder;)Lnet/minecraft/resources/Identifier;";

	private static final String FABRIC_MOD_JSON = "fabric.mod.json";
	private static final String MIXIN_CONFIG = "lampas2-overrides.statuseffectbars.mixins.json";

	/**
	 * The class is read with local variable names kept. Status Effect Bars' {@code render} is static, so slot 3 is its
	 * first int argument (x) and slot 4 its second (y).
	 */
	@Test
	void rendererDeclaresPublicStaticRenderNamedXAndYInSlots3And4() throws IOException {
		MethodNode render = method(read(RENDERER, ClassReader.SKIP_FRAMES), RENDER, RENDER_DESC);
		assertTrue((render.access & Opcodes.ACC_PUBLIC) != 0, RENDERER + "." + RENDER + " must be public");
		assertTrue((render.access & Opcodes.ACC_STATIC) != 0, RENDERER + "." + RENDER + " must be static");
		assertEquals("x", parameterName(render, 3));
		assertEquals("y", parameterName(render, 4));
	}

	/**
	 * The two hooks must select the x and y integer arguments of the same {@code render} method. Ordinal 0 is x and 1 is y,
	 * counted among the integer arguments because {@code argsOnly} is set.
	 */
	@Test
	void modifyVariableHooksTargetTheRenderArgumentsXAndY() throws IOException {
		ClassNode mixin = read(MIXIN);
		Map<String, Object> ordinals = new HashMap<>();
		for (MethodNode method : mixin.methods) {
			AnnotationNode hook = annotation(method, MODIFY_VARIABLE_ANNOTATION);
			if (hook == null) {
				continue;
			}
			assertEquals(Boolean.TRUE, value(hook, "argsOnly"), method.name + " must set argsOnly");
			assertEquals(List.of(RENDER + RENDER_DESC), value(hook, "method"), method.name + " @ModifyVariable method");
			ordinals.put(method.name, value(hook, "ordinal"));
		}
		assertEquals(Integer.valueOf(0), ordinals.get("lampas2$effectX"), "lampas2$effectX must select x with ordinal 0");
		assertEquals(Integer.valueOf(1), ordinals.get("lampas2$effectY"), "lampas2$effectY must select y with ordinal 1");
		assertEquals(2, ordinals.size(), "only the x and y hooks may modify render arguments");
	}

	/**
	 * HudMixin's callback calls {@code render} once and is injected at the {@code getMobEffectSprite} call inside
	 * {@code Hud#extractEffects}, the point where the x and y locals are the unshifted values.
	 */
	@Test
	void hudMixinDrawsTheBarFromItsExtractEffectsInjection() throws IOException {
		MethodNode callback = methodNamed(read(HUD_MIXIN), ON_EXTRACT_EFFECTS);

		List<MethodInsnNode> renders = calls(callback, Opcodes.INVOKESTATIC, RENDERER, RENDER, RENDER_DESC);
		assertEquals(1, renders.size(), ON_EXTRACT_EFFECTS + " must call " + RENDERER + "." + RENDER + " exactly once");

		AnnotationNode inject = annotation(callback, INJECT_ANNOTATION);
		assertNotNull(inject, ON_EXTRACT_EFFECTS + " must carry @Inject");
		assertEquals(List.of(EXTRACT_EFFECTS), value(inject, "method"));
		List<?> points = (List<?>) value(inject, "at");
		assertNotNull(points, "@Inject must declare an @At");
		assertEquals(1, points.size(), "@Inject must declare exactly one @At");
		AnnotationNode at = (AnnotationNode) points.get(0);
		assertEquals(AT_ANNOTATION, at.desc);
		assertEquals("INVOKE", value(at, "value"));
		assertEquals(HUD_TARGET, value(at, "target"));
	}

	/**
	 * Vanilla draws the two effect backgrounds with the six-argument {@code blitSprite} before it looks up the icon sprite.
	 * {@code EffectIconPosition} opens its capture window around those two blits, so both must come first. The check
	 * compares code order, which is the order the blits run in this method.
	 */
	@Test
	void vanillaExtractEffectsDrawsTwoBackgroundsBeforeTheIconSprite() throws IOException {
		MethodNode extractEffects = method(read(HUD), EXTRACT_EFFECTS, EXTRACT_EFFECTS_DESC);

		List<MethodInsnNode> backgrounds =
			calls(extractEffects, Opcodes.INVOKEVIRTUAL, GRAPHICS, BLIT_SPRITE, BLIT_SPRITE_DESC);
		assertEquals(2, backgrounds.size(), EXTRACT_EFFECTS + " must blit exactly two effect backgrounds");

		List<MethodInsnNode> icons =
			calls(extractEffects, Opcodes.INVOKESTATIC, HUD, GET_MOB_EFFECT_SPRITE, GET_MOB_EFFECT_SPRITE_DESC);
		assertFalse(icons.isEmpty(), EXTRACT_EFFECTS + " must look up the icon with " + GET_MOB_EFFECT_SPRITE);
		int firstIcon = extractEffects.instructions.indexOf(icons.get(0));
		for (MethodInsnNode background : backgrounds) {
			assertTrue(extractEffects.instructions.indexOf(background) < firstIcon,
				"each background blit must come before the first " + GET_MOB_EFFECT_SPRITE);
		}
	}

	@Test
	void fabricModJsonRegistersTheClientOnlyMixinConfig() throws IOException {
		JsonObject registered = null;
		for (JsonElement entry : readJson(FABRIC_MOD_JSON).getAsJsonArray("mixins")) {
			JsonObject mixin = entry.getAsJsonObject();
			if (MIXIN_CONFIG.equals(mixin.get("config").getAsString())) {
				registered = mixin;
			}
		}
		assertNotNull(registered, "fabric.mod.json must register " + MIXIN_CONFIG);
		assertEquals("client", registered.get("environment").getAsString());
	}

	private static MethodNode method(ClassNode owner, String name, String descriptor) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name) && method.desc.equals(descriptor)) {
				return method;
			}
		}
		throw new AssertionError(owner.name + "." + name + descriptor + " is not declared");
	}

	private static MethodNode methodNamed(ClassNode owner, String name) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name)) {
				return method;
			}
		}
		throw new AssertionError(owner.name + "." + name + " is not declared");
	}

	/** Returns the invocations in code order that match the opcode, owner, name and descriptor exactly. */
	private static List<MethodInsnNode> calls(MethodNode method, int opcode, String owner, String name, String descriptor) {
		List<MethodInsnNode> found = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions.toArray()) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner)
					&& call.name.equals(name) && call.desc.equals(descriptor)) {
				found.add(call);
			}
		}
		return found;
	}

	/** Returns the name of the local in {@code slot} whose scope starts at the method entry, which is the parameter there. */
	private static String parameterName(MethodNode method, int slot) {
		assertNotNull(method.localVariables, method.name + " must keep its local variable table");
		for (LocalVariableNode local : method.localVariables) {
			if (local.index == slot && method.instructions.indexOf(local.start) == 0) {
				return local.name;
			}
		}
		throw new AssertionError(method.name + " has no local in slot " + slot + " at its entry");
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

	private static ClassNode read(String internalName) throws IOException {
		return read(internalName, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
	}

	private static ClassNode read(String internalName, int flags) throws IOException {
		try (InputStream input = StatusEffectBarsContractTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			assertNotNull(input, internalName + " must be on the test classpath");
			ClassNode node = new ClassNode();
			new ClassReader(input.readAllBytes()).accept(node, flags);
			return node;
		}
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream in = StatusEffectBarsContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(in, "Missing " + resource + " on the test classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
