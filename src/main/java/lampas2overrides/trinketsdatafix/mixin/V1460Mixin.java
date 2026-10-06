package lampas2overrides.trinketsdatafix.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.types.templates.TypeTemplate;
import net.minecraft.util.datafix.schemas.V1460;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import lampas2overrides.trinketsdatafix.TrinketsDataFixMixinPlugin;
import lampas2overrides.trinketsdatafix.TrinketsSchemaRepair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.util.datafix.fixes.References;

/**
 * Repairs the exact audited V1460 template selected by the version and bytecode gate.
 *
 * <p>Trinkets 4.1.0 and 4.1.1 emit different wrappers, so each has an independent
 * exact template contract. Unknown shapes are returned unchanged. The repair keeps
 * the vanilla tail in place, types only the audited item lists, and retains unknown
 * values through nested remainder branches.</p>
 */
// Run after Trinkets' priority-1000 return modifier so the complete wrapper is available.
@Mixin(value = V1460.class, priority = 1500, remap = false)
abstract class V1460Mixin {
	@Unique private static final Logger lampas2$LOGGER = LoggerFactory.getLogger("lampas2-overrides/trinketsdatafix");
	@Unique private static final AtomicBoolean lampas2$UNKNOWN_SHAPE_LOGGED = new AtomicBoolean();

	@ModifyReturnValue(method = "lambda$registerTypes$2(Lcom/mojang/datafixers/schemas/Schema;)Lcom/mojang/datafixers/types/templates/TypeTemplate;", at = @At("RETURN"))
	private static TypeTemplate lampas2$repairPlayer(TypeTemplate result, Schema schema) {
		return lampas2$repair(result, References.ITEM_STACK.in(schema));
	}

	@ModifyReturnValue(method = "lambda$registerTypes$6(Lcom/mojang/datafixers/schemas/Schema;Ljava/util/Map;)Lcom/mojang/datafixers/types/templates/TypeTemplate;", at = @At("RETURN"))
	private static TypeTemplate lampas2$repairEntity(TypeTemplate result, Schema schema, Map<?, ?> entityTypes) {
		return lampas2$repair(result, References.ITEM_STACK.in(schema));
	}

	@Unique private static TypeTemplate lampas2$repair(TypeTemplate result, TypeTemplate itemStack) {
		TypeTemplate repaired = TrinketsSchemaRepair.repair(result, itemStack,
			TrinketsDataFixMixinPlugin.activeProfile());
		if (repaired == result && lampas2$UNKNOWN_SHAPE_LOGGED.compareAndSet(false, true)) {
			lampas2$LOGGER.warn("Trinkets V1460 schema shape did not match the audited repair contract; leaving it unchanged");
		}
		return repaired;
	}
}
