package lampas2overrides.additionallanterns.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Prevents Additional Lanterns from synchronously loading unloaded neighbor chunks
 * during redstone neighbor updates.
 */
@Mixin(targets = "com.supermartijn642.additionallanterns.VanillaLanternEvents", remap = false)
public abstract class VanillaLanternEventsMixin {

	@Inject(
		method = "handleLanternRedstone(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
		at = @At("HEAD"),
		cancellable = true,
		remap = false
	)
	private static void lampas2$skipUnloadedChunk(Level level, BlockPos pos, CallbackInfo ci) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;

		if (!serverLevel.getChunkSource().hasChunk(chunkX, chunkZ)) {
			ci.cancel();
		}
	}
}
