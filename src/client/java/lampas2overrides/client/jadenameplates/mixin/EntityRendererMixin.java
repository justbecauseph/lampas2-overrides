package lampas2overrides.client.jadenameplates.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Suppresses vanilla in-world entity nameplate and below-name scoreboard text submission when Jade is installed.
 *
 * <p>Wraps {@link SubmitNodeCollector#submitNameTag} inside {@link EntityRenderer#submitNameDisplay} without calling
 * the original operation. This suppresses both the score text and name tag submissions while letting the outer method
 * and any surrounding context lifecycles (such as Figura's {@code NameplateRenderContext.begin/end}) execute cleanly.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

	@WrapOperation(
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
		)
	)
	private void lampas2$hideNameTag(
		SubmitNodeCollector collector,
		PoseStack poseStack,
		Vec3 nameTagAttachment,
		int offset,
		Component name,
		boolean seeThrough,
		int lightCoords,
		CameraRenderState camera,
		Operation<Void> original
	) {
		// Intentionally omitted: suppresses name tag submission without aborting submitNameDisplay early.
	}
}
