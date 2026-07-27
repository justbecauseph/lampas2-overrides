package lampas2overrides.client.figurareplay.mixin.replaymod;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.client.figurareplay.FiguraReplayBridge;

/**
 * Carries the bridge's avatar data across ReplayMod's post-processing.
 *
 * <p>{@code apply} builds brand new output files and copies across only the metadata, markers, mod
 * info and resource packs, dropping everything else. This is not the niche path it sounds like:
 * ReplayMod writes a {@code _RM_START_CUT}/{@code _RM_SPLIT} marker pair whenever a recording is
 * stopped by hand, so post-processing runs on most recordings and would otherwise leave every one
 * of them with no avatars in it.
 *
 * <p>The original has to be read at HEAD because {@code apply} moves it into the raw folder before
 * producing any output.
 */
@Mixin(targets = "com.replaymod.editor.gui.MarkerProcessor", remap = false)
public class MarkerProcessorMixin {

	@Inject(
			method = "apply(Ljava/nio/file/Path;Ljava/util/function/Consumer;)Ljava/util/List;",
			at = @At("HEAD"))
	private static void lampas2$stashFiguraAvatars(Path replay, Consumer<Float> progress,
			CallbackInfoReturnable<List<?>> cir) {
		FiguraReplayBridge.beforePostProcessing(replay);
	}

	@Inject(
			method = "apply(Ljava/nio/file/Path;Ljava/util/function/Consumer;)Ljava/util/List;",
			at = @At("RETURN"))
	private static void lampas2$restoreFiguraAvatars(Path replay, Consumer<Float> progress,
			CallbackInfoReturnable<List<?>> cir) {
		FiguraReplayBridge.afterPostProcessing(replay, cir.getReturnValue());
	}
}
