package lampas2overrides.client.justenoughfilters;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Repairs Just Enough Filters 3.2.0's {@code @Inject} call sites after Mixin weaves them into R8-shrunk JEI 30.48.0.244
 * classes.
 *
 * <p>In JEI 30.48.0.244:
 * <ul>
 *   <li>{@code IngredientFilter#getIngredientListUncached(String)} reuses local slot 1 ({@code filterText}) for a
 *       {@code java.util.List}, while {@code JeiIngredientFilterMixin#jef$applyFilters} declares {@code String filterText}
 *       without reading it.</li>
 *   <li>{@code IngredientListOverlayController#updateBounds(IGuiProperties, Set)} reuses local slot 1
 *       ({@code guiProperties}) for a {@code boolean}, {@code Layout}, and {@code SearchAndConfigAreas}, while
 *       {@code JeiIngredientListOverlayControllerMixin#jef$updateJefBounds} declares both {@code guiProperties} and
 *       {@code guiExclusionAreas} without reading either.</li>
 * </ul>
 *
 * <p>After verifying that the merged handler method does not touch those local variable slots, this patcher replaces the
 * captured parameter {@code ALOAD} instructions immediately before the injected handler call with {@code ACONST_NULL}.
 * Any missing target method, missing handler call, unexpected local-variable access in the handler, or altered argument
 * load sequence throws {@link IllegalStateException} so class transformation fails loudly.
 */
public final class JustEnoughFiltersBytecodePatcher {

	public static final String INGREDIENT_FILTER_INTERNAL = "mezz/jei/gui/ingredients/IngredientFilter";
	public static final String GET_INGREDIENT_LIST_UNCACHED_NAME = "getIngredientListUncached";
	public static final String GET_INGREDIENT_LIST_UNCACHED_DESC =
		"(Ljava/lang/String;)Ljava/util/stream/Stream;";
	public static final String JEF_APPLY_FILTERS_SUFFIX = "$jef$applyFilters";
	public static final String JEF_APPLY_FILTERS_DESC =
		"(Ljava/lang/String;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

	public static final String OVERLAY_CONTROLLER_INTERNAL =
		"mezz/jei/gui/overlay/IngredientListOverlayController";
	public static final String UPDATE_BOUNDS_NAME = "updateBounds";
	public static final String UPDATE_BOUNDS_DESC =
		"(Lmezz/jei/api/gui/handlers/IGuiProperties;Ljava/util/Set;)V";
	public static final String JEF_UPDATE_BOUNDS_SUFFIX = "$jef$updateJefBounds";
	public static final String JEF_UPDATE_BOUNDS_DESC =
		"(Lmezz/jei/api/gui/handlers/IGuiProperties;Ljava/util/Set;"
			+ "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

	private static final String CALLBACK_INFO_INTERNAL =
		"org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final String CALLBACK_INFO_INIT_DESC = "(Ljava/lang/String;Z)V";

	private static final TargetSpec INGREDIENT_FILTER_SPEC = new TargetSpec(
		JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET,
		INGREDIENT_FILTER_INTERNAL,
		GET_INGREDIENT_LIST_UNCACHED_NAME,
		GET_INGREDIENT_LIST_UNCACHED_DESC,
		JEF_APPLY_FILTERS_SUFFIX,
		JEF_APPLY_FILTERS_DESC,
		new int[] {1}
	);

	private static final TargetSpec OVERLAY_CONTROLLER_SPEC = new TargetSpec(
		JustEnoughFiltersProfiles.OVERLAY_CONTROLLER_TARGET,
		OVERLAY_CONTROLLER_INTERNAL,
		UPDATE_BOUNDS_NAME,
		UPDATE_BOUNDS_DESC,
		JEF_UPDATE_BOUNDS_SUFFIX,
		JEF_UPDATE_BOUNDS_DESC,
		new int[] {1, 2}
	);

	private JustEnoughFiltersBytecodePatcher() {}

	public static void patch(String targetClassName, ClassNode targetClass) {
		if (JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET.equals(targetClassName)) {
			patchTarget(targetClass, INGREDIENT_FILTER_SPEC);
			return;
		}
		if (JustEnoughFiltersProfiles.OVERLAY_CONTROLLER_TARGET.equals(targetClassName)) {
			patchTarget(targetClass, OVERLAY_CONTROLLER_SPEC);
			return;
		}
		throw new IllegalStateException(
			"Unexpected target class for JustEnoughFilters bytecode patcher: " + targetClassName
		);
	}

	private static void patchTarget(ClassNode targetClass, TargetSpec spec) {
		if (targetClass == null || !spec.internalName().equals(targetClass.name)) {
			throw new IllegalStateException(
				"Expected ClassNode " + spec.internalName() + " but got "
					+ (targetClass == null ? "null" : targetClass.name)
			);
		}

		MethodNode targetMethod = findUniqueMethod(targetClass, spec.methodName(), spec.methodDesc());
		List<MethodInsnNode> callSites = findHandlerCallSites(targetMethod, spec);
		if (callSites.size() != 1) {
			throw new IllegalStateException(
				"Expected exactly 1 JustEnoughFilters handler call ending with " + spec.handlerSuffix()
					+ " in " + spec.internalName() + "." + spec.methodName() + spec.methodDesc()
					+ ", found " + callSites.size()
			);
		}

		MethodInsnNode callSite = callSites.get(0);
		MethodNode handlerMethod = findUniqueMethod(targetClass, callSite.name, spec.handlerDesc());
		assertHandlerDoesNotUseSlots(handlerMethod, spec);

		AbstractInsnNode callbackArgStart = findCallbackArgumentStart(callSite);
		VarInsnNode[] parameterLoads = new VarInsnNode[spec.unusedParameterSlots().length];
		AbstractInsnNode cursor = callbackArgStart;
		for (int i = spec.unusedParameterSlots().length - 1; i >= 0; i--) {
			int expectedSlot = spec.unusedParameterSlots()[i];
			cursor = cursor != null ? cursor.getPrevious() : null;
			if (!(cursor instanceof VarInsnNode varInsn)
				|| varInsn.getOpcode() != Opcodes.ALOAD
				|| varInsn.var != expectedSlot) {
				throw new IllegalStateException(
					"Expected ALOAD " + expectedSlot + " before JustEnoughFilters callback in "
						+ spec.internalName() + "." + spec.methodName() + spec.methodDesc()
						+ ", found " + describeInsn(cursor)
				);
			}
			parameterLoads[i] = varInsn;
		}

		AbstractInsnNode receiverLoad = cursor.getPrevious();
		if (!(receiverLoad instanceof VarInsnNode receiverVar)
			|| receiverVar.getOpcode() != Opcodes.ALOAD
			|| receiverVar.var != 0) {
			throw new IllegalStateException(
				"Expected receiver ALOAD 0 before JustEnoughFilters parameter loads in "
					+ spec.internalName() + "." + spec.methodName() + spec.methodDesc()
					+ ", found " + describeInsn(receiverLoad)
			);
		}

		for (VarInsnNode parameterLoad : parameterLoads) {
			targetMethod.instructions.set(parameterLoad, new InsnNode(Opcodes.ACONST_NULL));
		}
	}

	private static MethodNode findUniqueMethod(ClassNode classNode, String name, String desc) {
		MethodNode matched = null;
		for (MethodNode method : classNode.methods) {
			if (name.equals(method.name) && desc.equals(method.desc)) {
				if (matched != null) {
					throw new IllegalStateException(
						"Duplicate method " + classNode.name + "." + name + desc
					);
				}
				matched = method;
			}
		}
		if (matched == null) {
			throw new IllegalStateException("Missing method " + classNode.name + "." + name + desc);
		}
		return matched;
	}

	private static List<MethodInsnNode> findHandlerCallSites(MethodNode targetMethod, TargetSpec spec) {
		List<MethodInsnNode> callSites = new ArrayList<>();
		for (AbstractInsnNode insn = targetMethod.instructions.getFirst();
			insn != null;
			insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode methodInsn
				&& methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
				&& spec.internalName().equals(methodInsn.owner)
				&& methodInsn.name.startsWith("handler$")
				&& methodInsn.name.endsWith(spec.handlerSuffix())
				&& spec.handlerDesc().equals(methodInsn.desc)) {
				callSites.add(methodInsn);
			}
		}
		return callSites;
	}

	private static void assertHandlerDoesNotUseSlots(MethodNode handlerMethod, TargetSpec spec) {
		for (AbstractInsnNode insn = handlerMethod.instructions.getFirst();
			insn != null;
			insn = insn.getNext()) {
			if (insn instanceof VarInsnNode varInsn && containsSlot(spec.unusedParameterSlots(), varInsn.var)) {
				throw new IllegalStateException(
					"JustEnoughFilters handler " + spec.internalName() + "." + handlerMethod.name
						+ handlerMethod.desc + " unexpectedly accesses local slot " + varInsn.var
				);
			}
			if (insn instanceof IincInsnNode iincInsn && containsSlot(spec.unusedParameterSlots(), iincInsn.var)) {
				throw new IllegalStateException(
					"JustEnoughFilters handler " + spec.internalName() + "." + handlerMethod.name
						+ handlerMethod.desc + " unexpectedly increments local slot " + iincInsn.var
				);
			}
		}
	}

	private static AbstractInsnNode findCallbackArgumentStart(MethodInsnNode callSite) {
		AbstractInsnNode prev = callSite.getPrevious();
		if (prev instanceof VarInsnNode varInsn && varInsn.getOpcode() == Opcodes.ALOAD) {
			return varInsn;
		}
		if (prev instanceof InsnNode insn && insn.getOpcode() == Opcodes.ACONST_NULL) {
			return insn;
		}
		if (prev instanceof MethodInsnNode initInsn
			&& initInsn.getOpcode() == Opcodes.INVOKESPECIAL
			&& CALLBACK_INFO_INTERNAL.equals(initInsn.owner)
			&& "<init>".equals(initInsn.name)
			&& CALLBACK_INFO_INIT_DESC.equals(initInsn.desc)) {
			AbstractInsnNode cancellableInsn = initInsn.getPrevious();
			AbstractInsnNode idInsn = cancellableInsn != null ? cancellableInsn.getPrevious() : null;
			AbstractInsnNode dupInsn = idInsn != null ? idInsn.getPrevious() : null;
			AbstractInsnNode newInsn = dupInsn != null ? dupInsn.getPrevious() : null;
			if (cancellableInsn instanceof InsnNode
				&& (cancellableInsn.getOpcode() == Opcodes.ICONST_0
					|| cancellableInsn.getOpcode() == Opcodes.ICONST_1)
				&& idInsn instanceof LdcInsnNode
				&& dupInsn instanceof InsnNode
				&& dupInsn.getOpcode() == Opcodes.DUP
				&& newInsn instanceof TypeInsnNode typeInsn
				&& typeInsn.getOpcode() == Opcodes.NEW
				&& CALLBACK_INFO_INTERNAL.equals(typeInsn.desc)) {
				return newInsn;
			}
		}
		throw new IllegalStateException(
			"Unrecognized CallbackInfo argument sequence before "
				+ callSite.owner + "." + callSite.name + callSite.desc
		);
	}

	private static boolean containsSlot(int[] slots, int target) {
		for (int slot : slots) {
			if (slot == target) {
				return true;
			}
		}
		return false;
	}

	private static String describeInsn(AbstractInsnNode insn) {
		if (insn == null) {
			return "null";
		}
		if (insn instanceof VarInsnNode varInsn) {
			return "VarInsnNode(opcode=" + varInsn.getOpcode() + ", var=" + varInsn.var + ")";
		}
		return insn.getClass().getSimpleName() + "(opcode=" + insn.getOpcode() + ")";
	}

	private record TargetSpec(
		String className,
		String internalName,
		String methodName,
		String methodDesc,
		String handlerSuffix,
		String handlerDesc,
		int[] unusedParameterSlots
	) {}
}
