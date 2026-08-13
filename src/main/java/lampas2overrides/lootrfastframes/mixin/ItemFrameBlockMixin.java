package lampas2overrides.lootrfastframes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fuzs.fastitemframes.common.world.level.block.ItemFrameBlock;
import fuzs.fastitemframes.common.world.level.block.entity.ItemFrameBlockEntity;
import lampas2overrides.lootrfastframes.LootrFastItemFrame;
import lampas2overrides.lootrfastframes.LootrFastItemFrameActions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Preserves Lootr item-frame interaction, pick-block and projectile protection semantics. */
@Mixin(value = ItemFrameBlock.class, remap = false)
public abstract class ItemFrameBlockMixin {

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void lampas2$useLootrFrame(ItemStack held, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (level.getBlockEntity(pos) instanceof ItemFrameBlockEntity blockEntity
				&& blockEntity instanceof LootrFastItemFrame bridge && bridge.lampas2$isLootrFrame()) {
			cir.setReturnValue(LootrFastItemFrameActions.use(blockEntity, held, state, level, pos, player, hand));
		}
	}

	@Inject(method = "getCloneItemStack", at = @At("HEAD"), cancellable = true)
	private void lampas2$pickFrame(LevelReader level, BlockPos pos, BlockState state, boolean includeData,
			CallbackInfoReturnable<ItemStack> cir) {
		if (level.getBlockEntity(pos) instanceof LootrFastItemFrame bridge && bridge.lampas2$isLootrFrame()) {
			cir.setReturnValue(new ItemStack(Items.ITEM_FRAME));
		}
	}

	@Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
	private void lampas2$protectFromProjectiles(Level level, BlockState state, BlockHitResult hit,
			Projectile projectile, CallbackInfo ci) {
		if (level.getBlockEntity(hit.getBlockPos()) instanceof LootrFastItemFrame bridge
				&& bridge.lampas2$isLootrFrame()) {
			ci.cancel();
		}
	}
}
