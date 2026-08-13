package lampas2overrides.lootrfastframes;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import noobanidus.mods.lootr.common.api.interfaces.type.ILootrType;

/** Lootr data type for an item frame whose physical instance is now a block entity. */
public final class LootrFastItemFrameType implements ILootrType {

	public static final String NAME = "lampas2-overrides:lootr_item_frame_block";
	public static final LootrFastItemFrameType INSTANCE = new LootrFastItemFrameType();

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public @Nullable Block getReplacementBlock() {
		return null;
	}

	@Override
	public @Nullable EntityType<?> getReplacementEntity() {
		return null;
	}

	@Override
	public boolean canDecay() {
		return false;
	}

	@Override
	public boolean canRefresh() {
		return false;
	}
}
