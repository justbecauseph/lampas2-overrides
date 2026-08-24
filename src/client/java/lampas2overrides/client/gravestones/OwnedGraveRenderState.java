package lampas2overrides.client.gravestones;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Interface mixed into {@link net.pneumono.gravestones.content.TechnicalGravestoneBlockEntityRenderer.TechnicalRenderState}
 * to carry local player ownership and block state across render-state extraction and submission.
 */
public interface OwnedGraveRenderState {

	boolean lampas2$isOwnedByLocalPlayer();

	void lampas2$setOwnedByLocalPlayer(boolean owned);

	BlockState lampas2$getBlockState();

	void lampas2$setBlockState(BlockState blockState);
}
