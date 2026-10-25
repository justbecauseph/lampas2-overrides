package lampas2overrides.tridentcrits.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import lampas2overrides.tridentcrits.TridentCriticalStrikes;

/**
 * Takes the thrower's crit snapshot when a player's throw creates the trident, and rolls the crit around the one
 * {@code hurtOrSimulate} call in {@code onHitEntity} (inspected in 26.2), the call Critical Strike's own
 * {@code AbstractArrow.onHitEntity} wrap never reaches because this override does not call it.
 */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {

	@Unique
	@Nullable
	private TridentCriticalStrikes.Snapshot lampas$critSnapshot;

	@Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("TAIL"))
	private void lampas$takeCritSnapshot(Level level, LivingEntity owner, ItemStack stack, CallbackInfo ci) {
		this.lampas$critSnapshot = TridentCriticalStrikes.snapshotFor(owner);
	}

	@WrapOperation(
			method = "onHitEntity",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
	)
	private boolean lampas$criticalStrike(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
		return TridentCriticalStrikes.hurt(this.lampas$critSnapshot, ((Entity) (Object) this).getRandom(), target, source, amount,
				(critSource, critAmount) -> original.call(target, critSource, critAmount));
	}
}
