package lampas2overrides.crowdcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import lampas2overrides.crowdcontrol.CrowdControlImmunity;

/**
 * Makes Apotheosis elites and invaders (see {@link CrowdControlImmunity#isProtected}) immune to RPG Series crowd control,
 * by reusing Spell Engine's own boss gate in {@code SpellImpacts.performImpact} (inspected in 1.10.9+26.2). The class is named
 * by string, so Spell Engine stays off the compile classpath; {@code SpellEngineMixinPlugin} applies this whenever Spell Engine
 * is installed.
 *
 * <p>For STATUS_EFFECT impacts in ADD and SET mode, Spell Engine refuses an effect when
 * {@code target.is(#spell_engine:bosses) && (isMovementImpairing(effect) || disablesMobAI(effect))}. The wraps:
 * <ul>
 * <li>(a) a protected target passes the first operand, so its CC is refused as a boss's is;</li>
 * <li>(b) on a protected target, effects in {@code #lampas:spell_crowd_control} (Shatter and Demoralize) also count as
 * movement-impairing. Unprotected bosses keep Spell Engine's own classification;</li>
 * <li>(c) a protected target reads knockback resistance 1.0 for HARMFUL velocity impacts, so knock-ups and drags scale to
 * zero. Unprotected bosses keep their own knockback resistance.</li>
 * </ul>
 * Other debuffs, damage and damage knockback still apply. A refused impact returns false as it does for a boss.
 */
@Mixin(targets = "net.spell_engine.internals.impact.SpellImpacts", remap = false)
public abstract class SpellImpactsCrowdControlMixin {

	private static final String PERFORM_IMPACT = "performImpact(Lnet/minecraft/world/level/Level;"
			+ "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;"
			+ "Lnet/spell_engine/api/spell/Spell$Impact;Lnet/spell_engine/internals/SpellExecution$ImpactContext;"
			+ "Ljava/util/Collection;)Z";

	@WrapOperation(
			method = PERFORM_IMPACT,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;is(Lnet/minecraft/tags/TagKey;)Z")
	)
	private static boolean lampas$protectedFailsBossGate(Entity target, TagKey<EntityType<?>> tag, Operation<Boolean> original) {
		return original.call(target, tag) || CrowdControlImmunity.isProtected(target);
	}

	@WrapOperation(
			method = PERFORM_IMPACT,
			at = @At(value = "INVOKE",
					target = "Lnet/spell_engine/api/effect/StatusEffectClassification;isMovementImpairing(Lnet/minecraft/core/Holder;)Z")
	)
	private static boolean lampas$extraCrowdControl(Holder<MobEffect> effect, Operation<Boolean> original,
													@Local(argsOnly = true) Entity target) {
		return original.call(effect)
				|| (effect.is(CrowdControlImmunity.EXTRA_CROWD_CONTROL) && CrowdControlImmunity.isProtected(target));
	}

	@WrapOperation(
			method = PERFORM_IMPACT,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D")
	)
	private static double lampas$noHarmfulVelocity(LivingEntity target, Holder<Attribute> attribute, Operation<Double> original) {
		if (attribute.is(Attributes.KNOCKBACK_RESISTANCE) && CrowdControlImmunity.isProtected(target)) {
			return 1.0;
		}
		return original.call(target, attribute);
	}
}
