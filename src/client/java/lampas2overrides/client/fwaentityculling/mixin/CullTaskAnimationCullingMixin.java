package lampas2overrides.client.fwaentityculling.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tr7zw.entityculling.CullTask;
import dev.tr7zw.entityculling.versionless.occlusionculling.OcclusionCulling;
import dev.tr7zw.entityculling.versionless.occlusionculling.util.Vec3d;
import fr.madu59.fwa.FancyWorldAnimationsClient;
import fr.madu59.fwa.anims.Animation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Re-adds Fancy World Animations 1.3.4's animation occlusion culling against EntityCulling 1.12.0's types.
 *
 * <p>The logic is fwa's {@code CullTaskMixin#fwa$cullAnimations}, which the canceller removes for this pair. It runs at
 * the TAIL of {@code CullTask#cullEntities} on EntityCulling's cull thread, as the original did. An animation whose box
 * is missing or wider than EntityCulling's {@code hitboxLimit} is marked visible, so the cull is skipped for it.
 * {@code cameraMC} is part of the target signature and is not read here.
 */
@Mixin(value = CullTask.class, remap = false)
abstract class CullTaskAnimationCullingMixin {

	@Shadow
	private OcclusionCulling culling;

	@Shadow
	@Final
	private int hitboxLimit;

	@Unique
	private final Vec3d lampas2$animationMin = new Vec3d(0, 0, 0);

	@Unique
	private final Vec3d lampas2$animationMax = new Vec3d(0, 0, 0);

	@Inject(method = "cullEntities", at = @At("TAIL"), remap = false)
	private void lampas2$cullAnimations(Vec3 cameraMC, Vec3d camera, CallbackInfo ci) {
		for (Animation animation : FancyWorldAnimationsClient.animations.animations.values()) {
			if (!animation.hasInfiniteAnimation()) {
				continue;
			}
			AABB box = animation.getBoundingBox();
			if (box == null || box.getXsize() > hitboxLimit || box.getYsize() > hitboxLimit || box.getZsize() > hitboxLimit) {
				animation.setIsOcclusionCulled(false);
				continue;
			}
			lampas2$animationMin.set(box.minX, box.minY, box.minZ);
			lampas2$animationMax.set(box.maxX, box.maxY, box.maxZ);
			animation.setIsOcclusionCulled(!culling.isAABBVisible(lampas2$animationMin, lampas2$animationMax, camera));
		}
	}
}
