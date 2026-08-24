package lampas2overrides.client.gravestones.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import lampas2overrides.client.gravestones.OwnedGraveRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.pneumono.gravestones.content.TechnicalGravestoneBlockEntityRenderer;

/**
 * Implements {@link OwnedGraveRenderState} on Gravestones' technical render state.
 */
@Mixin(value = TechnicalGravestoneBlockEntityRenderer.TechnicalRenderState.class, remap = false)
public abstract class TechnicalRenderStateMixin implements OwnedGraveRenderState {

	@Unique
	private boolean lampas2$ownedByLocalPlayer;

	@Unique
	private BlockState lampas2$blockState;

	@Override
	public boolean lampas2$isOwnedByLocalPlayer() {
		return this.lampas2$ownedByLocalPlayer;
	}

	@Override
	public void lampas2$setOwnedByLocalPlayer(boolean owned) {
		this.lampas2$ownedByLocalPlayer = owned;
	}

	@Override
	public BlockState lampas2$getBlockState() {
		return this.lampas2$blockState;
	}

	@Override
	public void lampas2$setBlockState(BlockState blockState) {
		this.lampas2$blockState = blockState;
	}
}
