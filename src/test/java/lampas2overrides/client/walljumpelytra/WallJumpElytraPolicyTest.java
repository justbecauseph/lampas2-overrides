package lampas2overrides.client.walljumpelytra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lampas2overrides.client.walljumpelytra.WallJumpElytraPolicy.Snapshot;
import lampas2overrides.client.walljumpelytra.WallJumpElytraPolicy.WallClingAction;

class WallJumpElytraPolicyTest {

	private static Snapshot baseMidAir() {
		return new Snapshot(
			0,
			true,
			false,
			false,
			0,
			false,
			true,
			false,
			false,
			false,
			true,
			false,
			20,
			-0.2,
			false,
			0,
			false,
			false,
			false,
			false,
			true
		);
	}

	private static Snapshot withClingTicks(Snapshot s, int ticks) {
		return new Snapshot(
			ticks, s.canWallJump(), s.stopSlid(), s.wallJumpKeyDown(), s.ticksKeyDown(),
			s.hasAdjacentAllowedWall(), s.wallClingClearOfGround(), s.onClimbable(), s.onGround(),
			s.abilitiesFlying(), s.fluidEmpty(), s.handsBusy(), s.foodLevel(), s.deltaY(),
			s.doubleJumpEnabled(), s.doubleJumpCount(), s.doubleJumpKeyHeld(), s.passenger(),
			s.mayfly(), s.inUpperLiquid(), s.onFallDoubleJump()
		);
	}

	private static Snapshot withStartCling(
		boolean canWallJump,
		boolean stopSlid,
		boolean wallJumpKeyDown,
		int ticksKeyDown,
		boolean hasAdjacentAllowedWall,
		boolean wallClingClearOfGround,
		int foodLevel,
		double deltaY
	) {
		return new Snapshot(
			0,
			canWallJump,
			stopSlid,
			wallJumpKeyDown,
			ticksKeyDown,
			hasAdjacentAllowedWall,
			wallClingClearOfGround,
			false,
			false,
			false,
			true,
			false,
			foodLevel,
			deltaY,
			false,
			0,
			false,
			false,
			false,
			false,
			true
		);
	}

	private static Snapshot withDoubleJump(
		boolean enabled,
		int count,
		boolean keyHeld,
		boolean mayfly,
		boolean inUpperLiquid,
		int foodLevel,
		boolean onFallDoubleJump,
		double deltaY
	) {
		return new Snapshot(
			0,
			false,
			false,
			false,
			0,
			false,
			true,
			false,
			false,
			false,
			true,
			false,
			foodLevel,
			deltaY,
			enabled,
			count,
			keyHeld,
			false,
			mayfly,
			inUpperLiquid,
			onFallDoubleJump
		);
	}

	@Test
	void normalMidAirFallAllowsElytra() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();
		assertFalse(policy.shouldSuppressElytra(baseMidAir()));
	}

	@Test
	void activeWallClingSuppressesElytra() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();
		assertTrue(policy.shouldSuppressElytra(withClingTicks(baseMidAir(), 1)));
		assertTrue(policy.shouldSuppressElytra(withClingTicks(baseMidAir(), 15)));
	}

	@Test
	void startingWallClingSuppressesOnlyWhenAllClingConditionsHold() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();
		assertTrue(policy.shouldSuppressElytra(withStartCling(true, false, true, 0, true, true, 20, -0.1)));
		assertTrue(policy.shouldSuppressElytra(withStartCling(true, false, true, 2, true, true, 1, 0.2)));

		assertFalse(policy.shouldSuppressElytra(withStartCling(false, false, true, 0, true, true, 20, -0.1)));
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, true, true, 0, true, true, 20, -0.1)));
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, false, false, 0, true, true, 20, -0.1)));
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, false, true, 3, true, true, 20, -0.1)));
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, false, true, 0, false, true, 20, -0.1)));
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, false, true, 0, true, false, 20, -0.1)));
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, false, true, 0, true, true, 0, -0.1)));
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, false, true, 0, true, true, 20, 0.25)));
	}

	@Test
	void postWallJumpGraceArmsForEightTicksAndExpiresOnNinth() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();
		Object player = new Object();

		policy.onTickHead(player, 0, false);
		assertEquals(0, policy.graceTicks());
		assertFalse(policy.shouldSuppressElytra(baseMidAir()));

		// Wall jump happened on previous tick TAIL -> count rose from 0 to 1.
		policy.onTickHead(player, 1, false);
		assertEquals(WallJumpElytraPolicy.WALL_JUMP_GRACE_TICKS, policy.graceTicks());
		assertTrue(policy.shouldSuppressElytra(baseMidAir()));

		for (int tick = 2; tick <= WallJumpElytraPolicy.WALL_JUMP_GRACE_TICKS; tick++) {
			policy.onTickHead(player, 1, false);
			assertEquals(WallJumpElytraPolicy.WALL_JUMP_GRACE_TICKS - (tick - 1), policy.graceTicks());
			assertTrue(policy.shouldSuppressElytra(baseMidAir()), "tick " + tick + " must stay inside grace window");
		}

		// Ninth tick after wall jump: grace expires.
		policy.onTickHead(player, 1, false);
		assertEquals(0, policy.graceTicks());
		assertFalse(policy.shouldSuppressElytra(baseMidAir()));
	}

	@Test
	void chainedWallJumpRearmsGraceAndGroundOrPlayerSwitchClearsIt() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();
		Object player = new Object();

		policy.onTickHead(player, 0, false);
		policy.onTickHead(player, 1, false);
		policy.onTickHead(player, 1, false);
		assertEquals(WallJumpElytraPolicy.WALL_JUMP_GRACE_TICKS - 1, policy.graceTicks());

		// Second wall jump re-arms the full window.
		policy.onTickHead(player, 2, false);
		assertEquals(WallJumpElytraPolicy.WALL_JUMP_GRACE_TICKS, policy.graceTicks());

		// Leaving cling without jumping (wallJumpCount unchanged) does not arm grace.
		policy.onTickHead(player, 2, true);
		assertEquals(0, policy.graceTicks());
		policy.onTickHead(player, 2, false);
		assertEquals(0, policy.graceTicks());

		// Switching player instance resets grace even if the new player enters with a positive count.
		policy.onTickHead(player, 3, false);
		assertEquals(WallJumpElytraPolicy.WALL_JUMP_GRACE_TICKS, policy.graceTicks());
		Object otherPlayer = new Object();
		policy.onTickHead(otherPlayer, 5, false);
		assertEquals(0, policy.graceTicks());
		assertFalse(policy.shouldSuppressElytra(baseMidAir()));
	}

	@Test
	void availableDoubleJumpSuppressesElytraWithinDoubleJumpBounds() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();

		assertTrue(policy.shouldSuppressElytra(withDoubleJump(true, 1, false, false, false, 20, true, -1.2)));
		assertTrue(policy.shouldSuppressElytra(withDoubleJump(true, 1, false, false, false, 20, false, -0.8)));

		assertFalse(policy.shouldSuppressElytra(withDoubleJump(false, 1, false, false, false, 20, true, -0.2)));
		assertFalse(policy.shouldSuppressElytra(withDoubleJump(true, 0, false, false, false, 20, true, -0.2)));
		assertFalse(policy.shouldSuppressElytra(withDoubleJump(true, 1, true, false, false, 20, true, -0.2)));
		assertFalse(policy.shouldSuppressElytra(withDoubleJump(true, 1, false, true, false, 20, true, -0.2)));
		assertFalse(policy.shouldSuppressElytra(withDoubleJump(true, 1, false, false, true, 20, true, -0.2)));
		assertFalse(policy.shouldSuppressElytra(withDoubleJump(true, 1, false, false, false, 0, true, -0.2)));
		assertFalse(policy.shouldSuppressElytra(withDoubleJump(true, 1, false, false, false, 20, false, -0.85)));
	}

	@Test
	void blockListModesMatchWallJumpLogic() {
		List<String> list = List.of("minecraft:stone", "minecraft:glass");

		assertTrue(WallJumpElytraPolicy.isBlockAllowed("DISABLED", list, "minecraft:stone"));
		assertTrue(WallJumpElytraPolicy.isBlockAllowed("BLACKLIST", List.of(), "minecraft:stone"));
		assertTrue(WallJumpElytraPolicy.isBlockAllowed("BLACKLIST", list, "minecraft:stone", "minecraft:oak_planks"));
		assertFalse(WallJumpElytraPolicy.isBlockAllowed("BLACKLIST", list, "minecraft:stone", null));

		assertTrue(WallJumpElytraPolicy.isBlockAllowed("WHITELIST", list, null, "minecraft:glass"));
		assertFalse(WallJumpElytraPolicy.isBlockAllowed("WHITELIST", list, "minecraft:oak_planks", "minecraft:dirt"));
	}

	@Test
	void glidingAgainstWallWithKeyCancelsElytraAndLatchesClingBlockUntilRelease() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();
		Object player = new Object();
		policy.onTickHead(player, 0, false);

		// Gliding in open air blocks clinging without cancelling elytra or latching.
		assertEquals(WallClingAction.BLOCK, policy.evaluateWallCling(true, true, false));
		assertFalse(policy.isClingSuppressedUntilKeyRelease());

		// Gliding against a wall without pressing the cling key blocks clinging without cancelling elytra.
		assertEquals(WallClingAction.BLOCK, policy.evaluateWallCling(true, false, true));
		assertFalse(policy.isClingSuppressedUntilKeyRelease());

		// Gliding + Shift against an allowed wall cancels elytra and latches cling suppression.
		assertEquals(WallClingAction.CANCEL_ELYTRA_AND_BLOCK, policy.evaluateWallCling(true, true, true));
		assertTrue(policy.isClingSuppressedUntilKeyRelease());

		// Next tick: no longer gliding, Shift still held -> cling stays blocked and start-cling does not suppress Space.
		policy.onTickHead(player, 0, false);
		policy.releaseClingSuppressionIfNeeded(true, false);
		assertFalse(policy.shouldSuppressElytra(withStartCling(true, false, true, 1, true, true, 20, -0.1)));
		assertEquals(WallClingAction.BLOCK, policy.evaluateWallCling(false, true, true));
		assertTrue(policy.isClingSuppressedUntilKeyRelease());

		// Releasing Shift clears the latch and restores clinging.
		policy.releaseClingSuppressionIfNeeded(false, false);
		assertFalse(policy.isClingSuppressedUntilKeyRelease());
		assertEquals(WallClingAction.ALLOW, policy.evaluateWallCling(false, true, true));
		assertTrue(policy.shouldSuppressElytra(withStartCling(true, false, true, 1, true, true, 20, -0.1)));
	}

	@Test
	void groundTouchOrPlayerSwitchClearsPostCancelClingLatch() {
		WallJumpElytraPolicy policy = new WallJumpElytraPolicy();
		Object player = new Object();
		policy.onTickHead(player, 0, false);

		assertEquals(WallClingAction.CANCEL_ELYTRA_AND_BLOCK, policy.evaluateWallCling(true, true, true));
		assertTrue(policy.isClingSuppressedUntilKeyRelease());

		// Landing clears latch even while Shift remains held.
		policy.releaseClingSuppressionIfNeeded(true, true);
		assertFalse(policy.isClingSuppressedUntilKeyRelease());
		assertEquals(WallClingAction.ALLOW, policy.evaluateWallCling(false, true, true));

		// Re-latch then switch player token -> latch clears.
		assertEquals(WallClingAction.CANCEL_ELYTRA_AND_BLOCK, policy.evaluateWallCling(true, true, true));
		assertTrue(policy.isClingSuppressedUntilKeyRelease());
		policy.onTickHead(new Object(), 0, false);
		assertFalse(policy.isClingSuppressedUntilKeyRelease());
	}
}
