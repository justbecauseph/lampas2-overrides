package lampas2overrides.lootrfastframes.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fuzs.fastitemframes.common.client.renderer.blockentity.ItemFrameBlockRenderer;
import fuzs.fastitemframes.common.client.renderer.blockentity.state.ItemFrameBlockRenderState;
import fuzs.fastitemframes.common.world.level.block.entity.ItemFrameBlockEntity;
import lampas2overrides.lootrfastframes.LootrFastItemFrame;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.resources.model.BlockStateDefinitions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.client.ClientHooks;
import noobanidus.mods.lootr.common.client.entity.LootrBlockStateDefinitions;
import noobanidus.mods.lootr.common.client.state.LootrItemFrameRenderState;

/** Reuses Lootr's frame renderer while Fast Item Frames owns the physical block. */
@Mixin(value = ItemFrameBlockRenderer.class, remap = false)
public abstract class ItemFrameBlockRendererMixin {

	@Unique
	private BlockModelResolver lampas2$blockModelResolver;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lampas2$captureModelResolver(BlockEntityRendererProvider.Context context, CallbackInfo ci) {
		lampas2$blockModelResolver = context.blockModelResolver();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lampas2$extractLootrFrame(ItemFrameBlockEntity blockEntity,
			ItemFrameBlockRenderState state, float partialTick, Vec3 cameraPosition,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
		if (!(blockEntity instanceof LootrFastItemFrame bridge) || !bridge.lampas2$isLootrFrame()
				|| !(state.entityRenderState instanceof LootrItemFrameRenderState frameState)) {
			return;
		}

		ILootrBlockEntity lootr = (ILootrBlockEntity) blockEntity;
		Player player = ClientHooks.getPlayer();
		boolean visuallyOpen = player != null && lootr.hasClientOpened(player);
		boolean vanilla = LootrAPI.isVanillaTextures();
		BlockState fakeState = vanilla
				? BlockStateDefinitions.getItemFrameFakeState(false, false)
				: LootrBlockStateDefinitions.getItemFrameFakeState(visuallyOpen);

		lampas2$blockModelResolver.update(frameState.frameModel, fakeState,
				ItemFrameRenderer.BLOCK_DISPLAY_CONTEXT);
		frameState.visuallyOpen = visuallyOpen;
		frameState.vanilla = vanilla;
		frameState.isInvisible = false;
		state.isInvisible = false;
		if (visuallyOpen) {
			frameState.item.clear();
			frameState.mapId = null;
			frameState.nameTag = null;
		}
	}
}
