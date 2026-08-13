package lampas2overrides.lootrfastframes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fuzs.fastitemframes.common.handler.ItemFrameHandler;
import fuzs.fastitemframes.common.world.level.block.ItemFrameBlock;
import fuzs.fastitemframes.common.world.level.block.entity.ItemFrameBlockEntity;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import lampas2overrides.lootrfastframes.LootrFastItemFrame;
import lampas2overrides.lootrfastframes.LootrFastItemFrameActions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import noobanidus.mods.lootr.common.entity.LootrItemFrame;

/** Captures Lootr state during conversion and replaces Fast Item Frames' shared-loot break rule. */
@Mixin(value = ItemFrameHandler.class, remap = false)
public abstract class ItemFrameHandlerMixin {

	@Inject(method = "setItemFrameBlock", at = @At("TAIL"))
	private static void lampas2$initializeLootrBlock(ServerLevel level, BlockPos pos, BlockState state,
			ItemFrame itemFrame, CallbackInfo ci) {
		if (itemFrame instanceof LootrItemFrame lootrFrame
				&& level.getBlockEntity(pos) instanceof ItemFrameBlockEntity blockEntity
				&& blockEntity instanceof LootrFastItemFrame bridge) {
			bridge.lampas2$initializeFrom(lootrFrame);
			level.setBlock(pos, blockEntity.getBlockState().setValue(ItemFrameBlock.INVISIBLE, true),
					Block.UPDATE_ALL);
			blockEntity.markUpdated(level);
		}
	}

	@Inject(method = "onBreakBlock", at = @At("HEAD"), cancellable = true)
	private static void lampas2$usePerPlayerLoot(ServerLevel level, BlockPos pos, BlockState state,
			Player player, ItemStack held, CallbackInfoReturnable<EventResult> cir) {
		if (level.getBlockEntity(pos) instanceof ItemFrameBlockEntity blockEntity
				&& blockEntity instanceof LootrFastItemFrame bridge && bridge.lampas2$isLootrFrame()) {
			cir.setReturnValue(LootrFastItemFrameActions.breakBlock(level, pos, player, blockEntity));
		}
	}
}
