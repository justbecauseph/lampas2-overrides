package lampas2overrides.client.visualworkbench.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fuzs.puzzleslib.common.api.block.v1.BlockConversionHelper;
import lampas2overrides.client.visualworkbench.VisualWorkbenchTagFix;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Prevents Visual Workbench tag reload crashes during client configuration or replay transitions.
 *
 * <p>Puzzles Lib's {@link BlockConversionHelper#copyBoundTags(Block, Block)} throws an
 * {@link IllegalStateException} if the target block is non-empty and has different tags than
 * the source. ReplayMod playback and repeated client configuration reloads re-trigger Visual
 * Workbench's tag copy after tags were already bound.
 *
 * <p>This mixin intercepts calls targeting blocks in the {@code visualworkbench} namespace and
 * rebinds their tags to match the source block, preserving Puzzles Lib's strict invariant for
 * all other callers.
 */
@Mixin(value = BlockConversionHelper.class, remap = false)
public abstract class BlockConversionHelperMixin {

	@Inject(
			method = "copyBoundTags",
			at = @At("HEAD"),
			cancellable = true)
	private static void lampas2$allowVisualWorkbenchTagRebind(Block from, Block to, CallbackInfo ci) {
		if (from == null || to == null) {
			return;
		}

		Identifier targetId = BuiltInRegistries.BLOCK.getKey(to);
		if (targetId == null && to.builtInRegistryHolder().isBound()) {
			targetId = to.builtInRegistryHolder().key().identifier();
		}

		if (!VisualWorkbenchTagFix.isVisualWorkbenchTarget(targetId)) {
			return;
		}

		VisualWorkbenchTagFix.rebindTags(from, to, targetId);
		ci.cancel();
	}
}
