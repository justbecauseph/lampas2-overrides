package lampas2overrides.client.boatmask.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.client.boatmask.BoatWaterMaskCompatibility;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BoatRenderState;

@Mixin(value = BoatRenderer.class, remap = false)
public abstract class BoatRendererMixin {

	@Shadow @Final @Mutable private Model.Simple waterPatchModel;
	@Shadow @Final private EntityModel<BoatRenderState> model;

	@Inject(
		method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/geom/ModelLayerLocation;)V",
		at = @At("RETURN")
	)
	private void lampas$repairWaterMask(
		EntityRendererProvider.Context context,
		ModelLayerLocation modelId,
		CallbackInfo callbackInfo
	) {
		FabricLoader loader = FabricLoader.getInstance();
		String modId = modelId.model().getNamespace();
		String providerVersion = loader.getModContainer(modId)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse(null);
		String emfVersion = loader.getModContainer(BoatWaterMaskCompatibility.EMF_MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse(null);
		if (!BoatWaterMaskCompatibility.supportedEmfVersion(emfVersion)
			|| !BoatWaterMaskCompatibility.isAuditedLayer(modId, providerVersion, modelId)
			|| !BoatWaterMaskCompatibility.installedProviderMatches(loader, modId)
			|| !BoatWaterMaskCompatibility.requiredModsMatch(loader, modId)) {
			return;
		}

		BoatWaterMaskCompatibility.EmfRootInfo hullRoot = BoatWaterMaskCompatibility.inspectEmfRoot(this.model.root());
		BoatWaterMaskCompatibility.EmfRootInfo waterRoot = BoatWaterMaskCompatibility.inspectEmfRoot(this.waterPatchModel.root());
		BoatWaterMaskCompatibility.ResourceProof resourceProof =
			BoatWaterMaskCompatibility.inspectSelectedResource(context.getResourceManager(), emfVersion);

		if (BoatWaterMaskCompatibility.shouldRepair(
			modId, providerVersion, modelId, hullRoot, waterRoot, resourceProof)) {
			this.waterPatchModel = BoatWaterMaskCompatibility.newVanillaWaterPatchModel();
		}
	}
}
