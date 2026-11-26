package lampas2overrides.walljumpelytra;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side GameTest running with the real Wall-Jump TXF jar to verify that {@link WallJumpElytraNetworking}
 * registers {@link StopFallFlyingC2SPayload} and clears fall-flying state on a gliding {@link ServerPlayer}.
 */
public final class WallJumpElytraGameTests {

	@GameTest(maxTicks = 5)
	public void receiverRegistersAndCancelsFallFlyingOnlyWhenGliding(GameTestHelper helper) {
		helper.assertTrue(
			WallJumpElytraNetworking.isRegistered(),
			"WallJumpElytraNetworking must register StopFallFlyingC2SPayload when walljump is loaded"
		);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(player.isFallFlying(), "mock player must start not gliding");
		helper.assertFalse(
			WallJumpElytraNetworking.handleStopFallFlying(player),
			"handleStopFallFlying must return false when player is not gliding"
		);

		player.startFallFlying();
		helper.assertTrue(player.isFallFlying(), "mock player must enter fall-flying after startFallFlying()");

		helper.assertTrue(
			WallJumpElytraNetworking.handleStopFallFlying(player),
			"handleStopFallFlying must return true when player is gliding"
		);
		helper.assertFalse(player.isFallFlying(), "handleStopFallFlying must clear isFallFlying()");
		helper.assertFalse(
			WallJumpElytraNetworking.handleStopFallFlying(player),
			"repeated handleStopFallFlying after stopping must return false"
		);
		helper.succeed();
	}
}
