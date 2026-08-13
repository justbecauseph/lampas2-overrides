package lampas2overrides.lootrfastframes;

import fuzs.fastitemframes.common.init.ModRegistry;
import fuzs.fastitemframes.common.world.level.block.entity.ItemFrameBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.api.interfaces.wrapper.ILootrBlockEntityWrapper;

/** Lets Lootr resolve converted Fast Item Frames block entities for packets and commands. */
public final class LootrFastItemFrameWrapper implements ILootrBlockEntityWrapper<ItemFrameBlockEntity> {

	@Override
	public ILootrBlockEntity apply(ItemFrameBlockEntity blockEntity) {
		if (blockEntity instanceof LootrFastItemFrame bridge && bridge.lampas2$isLootrFrame()) {
			return (ILootrBlockEntity) blockEntity;
		}
		return null;
	}

	@Override
	public BlockEntityType<?> getBlockEntityType() {
		return ModRegistry.ITEM_FRAME_BLOCK_ENTITY.value();
	}
}
