package lampas2overrides.lootrfastframes;

import fuzs.fastitemframes.common.world.level.block.ItemFrameBlock;
import fuzs.fastitemframes.common.world.level.block.entity.ItemFrameBlockEntity;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.api.LootrRegistry;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.api.interfaces.inventory.ILootrInventory;

/** Lootr interaction rules applied to a converted Fast Item Frames block. */
public final class LootrFastItemFrameActions {

	private LootrFastItemFrameActions() {
	}

	public static InteractionResult use(ItemFrameBlockEntity blockEntity, ItemStack held, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			if (!held.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("lootr.message.cannot_insert"));
			} else {
				level.playSound(null, pos, blockEntity.getRotateItemSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
				level.setBlock(pos, state.cycle(ItemFrameBlock.ROTATION), Block.UPDATE_ALL);
				level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);
			}
			return InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS;
	}

	public static EventResult breakBlock(ServerLevel level, BlockPos pos, Player player,
			ItemFrameBlockEntity blockEntity) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return EventResult.INTERRUPT;
		}

		boolean droppedPersonalItem = dropPersonalItem(level, pos, serverPlayer, blockEntity);
		if (!droppedPersonalItem) {
			messageIfProtected(serverPlayer);
		}

		return canBreak(serverPlayer) ? EventResult.PASS : EventResult.INTERRUPT;
	}

	public static boolean canBreak(Player player) {
		if (LootrAPI.canDestroyOrBreak(player)) {
			return true;
		}
		if (LootrAPI.isBreakDisabled()) {
			return player.getAbilities().instabuild && player.isShiftKeyDown();
		}
		return player.isShiftKeyDown();
	}

	private static boolean dropPersonalItem(ServerLevel level, BlockPos pos, ServerPlayer player,
			ItemFrameBlockEntity blockEntity) {
		ILootrBlockEntity lootr = (ILootrBlockEntity) blockEntity;
		ILootrInventory inventory = LootrAPI.getInventory(lootr, player);
		if (inventory == null || inventory.getItem(0).isEmpty()) {
			return false;
		}

		if (!lootr.hasServerOpened(player)) {
			player.awardStat(LootrRegistry.getLootedStat());
			LootrRegistry.getStatTrigger().trigger(player);
		}

		ItemStack dropped = inventory.getItem(0).copy();
		inventory.setItem(0, ItemStack.EMPTY);
		inventory.setChanged();
		level.playSound(null, pos, blockEntity.getRemoveItemSound(), SoundSource.BLOCKS, 1.0F, 1.0F);

		Vec3 dropPos = Vec3.atCenterOf(pos).relative(blockEntity.getBlockState().getValue(ItemFrameBlock.FACING), -0.25);
		ItemEntity itemEntity = new ItemEntity(level, dropPos.x(), dropPos.y(), dropPos.z(), dropped);
		itemEntity.setDefaultPickUpDelay();
		level.addFreshEntity(itemEntity);

		lootr.performTrigger(player);
		if (lootr.addOpener(player)) {
			lootr.performOpen(player);
		}
		((LootrFastItemFrame) blockEntity).lampas2$markOpened();
		lootr.performUpdate(player);
		return true;
	}

	private static void messageIfProtected(ServerPlayer player) {
		if (LootrAPI.canDestroyOrBreak(player)) {
			return;
		}
		if (LootrAPI.isBreakDisabled()) {
			if (player.getAbilities().instabuild) {
				if (!player.isShiftKeyDown()) {
					player.sendSystemMessage(Component.translatable("lootr.message.cannot_break_sneak")
							.setStyle(LootrAPI.getChatStyle()));
				}
			} else {
				player.sendSystemMessage(Component.translatable("lootr.message.cannot_break")
						.setStyle(LootrAPI.getChatStyle()));
			}
		} else if (!player.isShiftKeyDown()) {
			player.sendSystemMessage(Component.translatable("lootr.message.cart_should_sneak")
					.setStyle(LootrAPI.getChatStyle()));
			player.sendSystemMessage(Component.translatable("lootr.message.cart_should_sneak2")
					.setStyle(LootrAPI.getChatStyle()));
		}
	}
}
