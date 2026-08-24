package lampas2overrides.client.gravestones.mixin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import lampas2overrides.client.gravestones.OwnedGraveRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.pneumono.gravestones.content.AbstractGravestoneBlockEntityRenderer;

/**
 * Submits a glowing outline pass for the local player's death gravestones.
 */
@Mixin(value = AbstractGravestoneBlockEntityRenderer.class, remap = false)
public abstract class AbstractGravestoneBlockEntityRendererMixin {

	@Unique
	private static final int OUTLINE_COLOR_WHITE = 0xFFFFFFFF;

	@Unique
	private static BlockStateModelSet lampas2$lastModelSet;

	@Unique
	private static final Map<BlockState, List<BlockStateModelPart>> lampas2$modelPartsCache = new IdentityHashMap<>(4);

	@Unique
	private static List<BlockStateModelPart> lampas2$getOrComputeParts(BlockState blockState, BlockStateModelSet modelSet) {
		if (lampas2$lastModelSet != modelSet) {
			lampas2$modelPartsCache.clear();
			lampas2$lastModelSet = modelSet;
		}

		List<BlockStateModelPart> parts = lampas2$modelPartsCache.get(blockState);
		if (parts == null) {
			BlockStateModel model = modelSet.get(blockState);
			if (model == null) {
				parts = List.of();
			} else {
				List<BlockStateModelPart> collected = new ArrayList<>();
				model.collectParts(RandomSource.create(0L), collected);
				parts = List.copyOf(collected);
			}
			lampas2$modelPartsCache.put(blockState, parts);
		}
		return parts;
	}

	@Inject(
		method = "submit(Lnet/pneumono/gravestones/content/AbstractGravestoneBlockEntityRenderer$RenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("TAIL")
	)
	private void lampas2$submitGraveOutline(
		AbstractGravestoneBlockEntityRenderer.RenderState renderState,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		if (renderState instanceof OwnedGraveRenderState ownedState && ownedState.lampas2$isOwnedByLocalPlayer()) {
			BlockState blockState = ownedState.lampas2$getBlockState();
			if (blockState == null) {
				return;
			}

			BlockStateModelSet modelSet = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
			if (modelSet == null) {
				return;
			}

			List<BlockStateModelPart> parts = lampas2$getOrComputeParts(blockState, modelSet);
			if (!parts.isEmpty()) {
				submitNodeCollector.submitBlockModel(
					poseStack,
					RenderTypes.outline(TextureAtlas.LOCATION_BLOCKS),
					parts,
					BlockModelRenderState.EMPTY_TINTS,
					15728880,
					OverlayTexture.NO_OVERLAY,
					OUTLINE_COLOR_WHITE
				);
			}
		}
	}
}
