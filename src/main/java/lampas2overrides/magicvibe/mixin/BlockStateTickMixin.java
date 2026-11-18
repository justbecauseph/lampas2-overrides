package lampas2overrides.magicvibe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.magicvibe.MagicVibeTicks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Drops every scheduled block tick of a Magic Vibe Decorations block before the block's {@code tick} runs. The tick does not
 * reschedule itself, so a placed crystal ticks at most once and does nothing.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateTickMixin {

	@Shadow
	public abstract Block getBlock();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void lampas2$skipMagicVibeTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (MagicVibeTicks.isMagicVibeBlock(getBlock())) {
			ci.cancel();
		}
	}
}
