package lampas2overrides.client.gravestones.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.client.gravestones.OwnedGraveRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;
import net.pneumono.gravestones.block.TechnicalGravestoneBlockEntity;
import net.pneumono.gravestones.content.TechnicalGravestoneBlockEntityRenderer;
import net.pneumono.gravestones.multiversion.GraveOwner;

/**
 * Suppresses sign inscription text from player death graves and extracts local player ownership
 * onto the render state.
 */
@Mixin(value = TechnicalGravestoneBlockEntityRenderer.class, remap = false)
public abstract class TechnicalGravestoneBlockEntityRendererMixin {

	@Unique
	private static final SignText BLANK_SIGN_TEXT = new SignText();

	@Inject(
		method = "getSignText(Lnet/pneumono/gravestones/block/TechnicalGravestoneBlockEntity;)Lnet/minecraft/world/level/block/entity/SignText;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void lampas2$hideTechnicalGraveText(TechnicalGravestoneBlockEntity entity, CallbackInfoReturnable<SignText> cir) {
		cir.setReturnValue(BLANK_SIGN_TEXT);
	}

	@Inject(
		method = "extractRenderState(Lnet/pneumono/gravestones/block/TechnicalGravestoneBlockEntity;Lnet/pneumono/gravestones/content/TechnicalGravestoneBlockEntityRenderer$TechnicalRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At("TAIL")
	)
	private void lampas2$extractOwnership(
		TechnicalGravestoneBlockEntity entity,
		TechnicalGravestoneBlockEntityRenderer.TechnicalRenderState renderState,
		float partialTicks,
		Vec3 cameraPos,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		CallbackInfo ci
	) {
		LocalPlayer player = Minecraft.getInstance().player;
		GraveOwner graveOwner = entity.getGraveOwner();
		UUID ownerUuid = graveOwner != null ? graveOwner.getUuid() : null;
		boolean isOwnedByLocalPlayer = ownerUuid != null && player != null && ownerUuid.equals(player.getUUID());

		if (renderState instanceof OwnedGraveRenderState ownedState) {
			ownedState.lampas2$setOwnedByLocalPlayer(isOwnedByLocalPlayer);
			ownedState.lampas2$setBlockState(entity.getBlockState());
		}
	}
}
