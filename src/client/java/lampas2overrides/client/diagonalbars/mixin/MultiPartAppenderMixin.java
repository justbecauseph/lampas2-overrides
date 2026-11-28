package lampas2overrides.client.diagonalbars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;

import lampas2overrides.client.diagonalbars.DiagonalBarsPost;
import net.minecraft.client.renderer.block.dispatch.multipart.Condition;
import net.minecraft.client.renderer.block.dispatch.multipart.Selector;

/**
 * Diagonal Blocks 26.2.0's {@code MultiPartAppender#appendDiagonalSelectors} appends a rotated copy of each selector whose
 * condition names a direction but has no {@code true} term. That is the second of its three {@code appendNewSelector}
 * calls (ordinal 1). A bar-style post's rotated copy z-fights with the rotated arms on straight north-east/south-west runs.
 * This wraps the Condition argument of that call and replaces it with one that never matches when the source selector is
 * a bar-style post. Other selectors, including glass panes, keep the rotated copy. The mixin names Diagonal Blocks only by
 * string, so nothing from it is compiled in.
 */
@Mixin(targets = "fuzs.diagonalblocks.common.impl.client.resources.model.MultiPartAppender", remap = false)
abstract class MultiPartAppenderMixin {

	@ModifyArg(method = "appendDiagonalSelectors",
		at = @At(value = "INVOKE",
			target = "Lfuzs/diagonalblocks/common/impl/client/resources/model/MultiPartAppender;appendNewSelector(Lnet/minecraft/client/renderer/block/dispatch/multipart/Condition;Lnet/minecraft/client/renderer/block/dispatch/multipart/Selector;Lfuzs/diagonalblocks/common/api/v2/util/EightWayDirection;Ljava/util/List;)V",
			ordinal = 1),
		index = 0)
	private static Condition lampas2$hideRotatedBarsPost(Condition rotated, @Local(ordinal = 0) Selector selector) {
		// Ordinal 0 is the loop variable at LVT slot 5. The other Selector in scope here is newSelector at slot 12.
		return DiagonalBarsPost.rotatedCondition(rotated, selector.condition());
	}
}
