package lampas2overrides.magicvibe;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Live coverage of the Magic Vibe Decorations tick cancel on a GameTest server running the real Magic Vibe Decorations 3.0.1.
 * Each lane places one protective crystal on a stone pad with its target mob two blocks away, with no AI.
 *
 * <p>Expected behaviour without the cancel comes from the 3.0.1 bytecode: placing a crystal schedules a tick 10 ticks out, and
 * that tick deletes Endermen and Phantoms within 10 blocks, deals 50 damage to Creepers within 10 blocks, sets Zombies and
 * Skeletons within 7.5 blocks on fire for 100 ticks, and schedules the next tick. With the cancel, the first tick does none of
 * that, so after 30 ticks every mob is still alive at full health and not burning, and no tick is scheduled at any crystal.
 */
public final class MagicVibeTicksGameTests {

	@GameTest(maxTicks = 60)
	public void protectiveCrystalsDoNothingWhenTheyTick(GameTestHelper helper) {
		Lane ender = new Lane(helper, 1, "ender_amethyst_crystal", EntityTypes.ENDERMAN);
		Lane overworld = new Lane(helper, 3, "overworld_secret_crystal", EntityTypes.CREEPER);
		// Husks are Zombies to the Nether Fire crystal and do not burn in daylight.
		Lane nether = new Lane(helper, 5, "nether_fire_crystal", EntityTypes.HUSK);
		Lane[] lanes = { ender, overworld, nether };
		for (Lane lane : lanes) {
			helper.assertTrue(lane.tickScheduled(), "placing " + lane.crystal + " did not schedule a tick");
		}

		helper.runAfterDelay(30, () -> {
			for (Lane lane : lanes) {
				helper.assertBlockPresent(lane.block, lane.crystalPos);
				helper.assertTrue(!lane.target.isRemoved(), "the " + lane.crystal + " lane's mob was removed");
				helper.assertValueEqual(lane.target.getHealth(), lane.target.getMaxHealth(), lane.crystal + " lane mob health");
				helper.assertValueEqual(lane.target.getRemainingFireTicks() <= 0, true, lane.crystal + " lane mob not burning");
				helper.assertTrue(!lane.tickScheduled(), lane.crystal + " scheduled another tick");
			}
			helper.succeed();
		});
	}

	private static final class Lane {
		private final GameTestHelper helper;
		private final String crystal;
		private final Block block;
		private final BlockPos crystalPos;
		private final Mob target;

		Lane(GameTestHelper helper, int x, String crystal, EntityType<? extends Mob> mob) {
			this.helper = helper;
			this.crystal = crystal;
			Identifier id = Identifier.fromNamespaceAndPath(MagicVibeTicks.MOD_ID, crystal);
			block = BuiltInRegistries.BLOCK.get(id)
				.orElseThrow(() -> new IllegalStateException("block not registered: " + id))
				.value();
			helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
			crystalPos = new BlockPos(x, 2, 1);
			helper.setBlock(crystalPos, block);

			target = mob.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
			helper.assertTrue(target != null, "could not create the " + crystal + " lane's mob");
			target.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, 3))));
			target.setNoAi(true);
			helper.getLevel().addFreshEntity(target);
		}

		boolean tickScheduled() {
			return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(crystalPos), block);
		}
	}
}
