package lampas2overrides.client.walljumpelytra;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Pure state machine and predicate deciding when Wall-Jump TXF activity should suppress vanilla jump-to-elytra
 * activation. Kept free of Minecraft classes so unit tests run directly against it.
 */
public final class WallJumpElytraPolicy {

	public static final int WALL_JUMP_GRACE_TICKS = 8;

	/**
	 * Player and Wall-Jump TXF state captured inside {@code LocalPlayer#aiStep} right before vanilla's
	 * {@code tryToStartFallFlying()} call.
	 */
	public record Snapshot(
		int ticksWallClinged,
		boolean canWallJump,
		boolean stopSlid,
		boolean wallJumpKeyDown,
		int ticksKeyDown,
		boolean hasAdjacentAllowedWall,
		boolean wallClingClearOfGround,
		boolean onClimbable,
		boolean onGround,
		boolean abilitiesFlying,
		boolean fluidEmpty,
		boolean handsBusy,
		int foodLevel,
		double deltaY,
		boolean doubleJumpEnabled,
		int doubleJumpCount,
		boolean doubleJumpKeyHeld,
		boolean passenger,
		boolean mayfly,
		boolean inUpperLiquid,
		boolean onFallDoubleJump
	) {
	}

	private WeakReference<Object> trackedPlayer = new WeakReference<>(null);
	private int lastWallJumpCount;
	private int graceTicks;

	/**
	 * Updates the post-wall-jump grace counter at the start of {@code LocalPlayer#aiStep}, after the previous tick's
	 * {@code TAIL} Wall-Jump TXF logic has finished.
	 */
	public void onTickHead(Object playerToken, int currentWallJumpCount, boolean resetGrace) {
		if (playerToken == null) {
			trackedPlayer = new WeakReference<>(null);
			lastWallJumpCount = 0;
			graceTicks = 0;
			return;
		}
		if (trackedPlayer.get() != playerToken) {
			trackedPlayer = new WeakReference<>(playerToken);
			lastWallJumpCount = currentWallJumpCount;
			graceTicks = 0;
			return;
		}
		if (resetGrace) {
			graceTicks = 0;
			lastWallJumpCount = currentWallJumpCount;
			return;
		}
		if (currentWallJumpCount > lastWallJumpCount) {
			graceTicks = WALL_JUMP_GRACE_TICKS;
			lastWallJumpCount = currentWallJumpCount;
			return;
		}
		lastWallJumpCount = currentWallJumpCount;
		if (graceTicks > 0) {
			graceTicks--;
		}
	}

	public int graceTicks() {
		return graceTicks;
	}

	public boolean shouldSuppressElytra(Snapshot s) {
		if (s.ticksWallClinged() > 0) {
			return true;
		}
		if (isStartingWallCling(s)) {
			return true;
		}
		if (graceTicks > 0) {
			return true;
		}
		return hasAvailableDoubleJump(s);
	}

	private static boolean isStartingWallCling(Snapshot s) {
		return s.canWallJump()
			&& !s.stopSlid()
			&& s.wallJumpKeyDown()
			&& s.ticksKeyDown() < 3
			&& s.hasAdjacentAllowedWall()
			&& s.wallClingClearOfGround()
			&& !s.onClimbable()
			&& !s.onGround()
			&& !s.abilitiesFlying()
			&& s.fluidEmpty()
			&& !s.handsBusy()
			&& s.foodLevel() >= 1
			&& s.deltaY() <= 0.2;
	}

	private static boolean hasAvailableDoubleJump(Snapshot s) {
		return s.doubleJumpEnabled()
			&& s.doubleJumpCount() > 0
			&& !s.doubleJumpKeyHeld()
			&& !s.onGround()
			&& !s.passenger()
			&& !s.mayfly()
			&& !s.inUpperLiquid()
			&& s.foodLevel() > 0
			&& (s.onFallDoubleJump() || s.deltaY() >= -0.8);
	}

	/** Matches {@code WallJumpLogic.areBlocksAllowed(String...)} against {@code ModConfig.blockListMode}. */
	public static boolean isBlockAllowed(String blockListModeName, List<String> blockList, String... blockIds) {
		if ("DISABLED".equals(blockListModeName) || blockList == null || blockList.isEmpty()) {
			return true;
		}
		for (String blockId : blockIds) {
			if (blockId == null) {
				continue;
			}
			if ("BLACKLIST".equals(blockListModeName)) {
				if (!blockList.contains(blockId)) {
					return true;
				}
			} else if ("WHITELIST".equals(blockListModeName)) {
				if (blockList.contains(blockId)) {
					return true;
				}
			} else {
				return true;
			}
		}
		return false;
	}
}
