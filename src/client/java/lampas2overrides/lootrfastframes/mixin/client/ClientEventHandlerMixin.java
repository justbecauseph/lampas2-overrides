package lampas2overrides.lootrfastframes.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fuzs.fastitemframes.common.client.handler.ClientEventHandler;
import fuzs.fastitemframes.common.world.level.block.entity.ItemFrameBlockEntity;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import lampas2overrides.lootrfastframes.LootrFastItemFrame;
import lampas2overrides.lootrfastframes.LootrFastItemFrameActions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;

/** Prevents Fast Item Frames' shared item from permanently blocking client-side breaking. */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class ClientEventHandlerMixin {

	@Inject(method = "onAttackBlock", at = @At("HEAD"), cancellable = true)
	private static void lampas2$handleLootrFrame(Player player, Level level, InteractionHand hand,
			BlockPos pos, Direction direction, CallbackInfoReturnable<EventResult> cir) {
		if (level.getBlockEntity(pos) instanceof ItemFrameBlockEntity blockEntity
				&& blockEntity instanceof LootrFastItemFrame bridge && bridge.lampas2$isLootrFrame()) {
			ILootrBlockEntity lootr = (ILootrBlockEntity) blockEntity;
			boolean hasPersonalItem = !lootr.hasClientOpened(player);
			cir.setReturnValue(hasPersonalItem || !LootrFastItemFrameActions.canBreak(player)
					? EventResult.INTERRUPT : EventResult.PASS);
		}
	}
}
