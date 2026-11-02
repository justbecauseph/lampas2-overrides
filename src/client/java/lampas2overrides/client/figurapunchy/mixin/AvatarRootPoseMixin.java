package lampas2overrides.client.figurapunchy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Draws a player's Figura avatar under the player model's root part, as vanilla draws the model itself.
 *
 * <p>Figura draws the avatar from the PoseStack {@code LivingEntityRenderer#submit} holds after
 * {@code setupAnim}, and copies the pose of the head, body, arm and leg parts, but never the root
 * part they hang from. Punchy! Moves lays the body flat for elytra flight and swimming by rotating
 * that root part, and switches off vanilla's matching PoseStack tilt in
 * {@code AvatarRenderer#setupRotations}, so an avatar stood upright while the vanilla model lay flat.
 * With the root at rest, as it is without Punchy! Moves, the transform is the identity.
 */
@Mixin(targets = "org.figuramc.figura.avatar.Avatar", remap = false)
public class AvatarRootPoseMixin {

	private static final String RENDER = "render(Lnet/minecraft/world/entity/Entity;FFFLcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/client/renderer/entity/LivingEntityRenderer;"
		+ "Lorg/figuramc/figura/model/rendering/PartFilterScheme;ZZF)V";

	@Unique
	private boolean lampas2$rootPushed;

	@Inject(method = RENDER, at = @At("HEAD"))
	private void lampas2$pushModelRoot(Entity entity, float yaw, float delta, float alpha, PoseStack stack,
		SubmitNodeCollector collector, int light, int overlay, LivingEntityRenderer<?, ?, ?> renderer, @Coerce Object filter,
		boolean translucent, boolean glowing, float fade, CallbackInfo ci) {
		if (entity instanceof Player && renderer != null && renderer.getModel() instanceof PlayerModel model) {
			stack.pushPose();
			model.root().translateAndRotate(stack);
			lampas2$rootPushed = true;
		}
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void lampas2$popModelRoot(Entity entity, float yaw, float delta, float alpha, PoseStack stack,
		SubmitNodeCollector collector, int light, int overlay, LivingEntityRenderer<?, ?, ?> renderer, @Coerce Object filter,
		boolean translucent, boolean glowing, float fade, CallbackInfo ci) {
		if (lampas2$rootPushed) {
			lampas2$rootPushed = false;
			stack.popPose();
		}
	}
}
