package lampas2overrides.client.figurareplay.mixin.figura;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;

/**
 * Runs Blockbench animations on the replay's clock instead of the wall clock.
 *
 * <p>{@code TimeController} measures how far to advance an animation from {@code Util.getMillis()},
 * which is right for live play and wrong for anything else. Playing a replay at 2x would run
 * animations at half the speed of the world around them; a video export, which renders frames at
 * whatever rate the machine manages rather than at real time, would be off by however much that
 * differs — often by a factor of ten or more.
 *
 * <p>Swapping the clock rather than the resulting delta leaves the controller's own pause and
 * resume bookkeeping untouched.
 */
@Mixin(targets = "org.figuramc.figura.animation.TimeController", remap = false)
public class TimeControllerMixin {

	@Redirect(
			method = {"init()V", "tick()V", "pause()V", "resume()V"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"),
			require = 4)
	private long lampas2$replayClock() {
		return FiguraReplayBridge.animationMillis();
	}
}
