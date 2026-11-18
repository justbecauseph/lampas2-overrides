package lampas2overrides.magicvibe;

import net.minecraft.world.level.block.Block;

/**
 * Identifies Magic Vibe Decorations blocks, whose scheduled ticks the {@code magicvibe} mixin cancels.
 *
 * <p>Every Magic Vibe Decorations block runs its crystal effects (deleting Endermen and Phantoms, damaging Creepers, setting
 * Zombies and Skeletons on fire) and its ambient particles and sounds from {@code Block.tick}, rescheduled every 10 ticks. The
 * mod's classes were renamed in 4.0.0, so the check uses the block's registry namespace, which both 3.0.1 and 4.0.0 keep.
 */
public final class MagicVibeTicks {

	public static final String MOD_ID = "magic_vibe_decorations";

	private MagicVibeTicks() {
	}

	public static boolean isMagicVibeBlock(Block block) {
		return MOD_ID.equals(block.builtInRegistryHolder().key().identifier().getNamespace());
	}
}
