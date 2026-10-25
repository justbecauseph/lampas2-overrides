package lampas2overrides.tridentcrits.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import lampas2overrides.tridentcrits.TridentCriticalStrikes;

/**
 * Records the thrower's Critical Strike stats for the whole of {@code releaseUsing}, which creates the {@code ThrownTrident}.
 * At its start the trident is still in hand, so its modifiers are still on the player.
 */
@Mixin(TridentItem.class)
public abstract class TridentItemMixin {

	@WrapMethod(method = "releaseUsing")
	private boolean lampas$recordThrowerCrits(ItemStack stack, Level level, LivingEntity user, int timeLeft, Operation<Boolean> original) {
		Object previous = TridentCriticalStrikes.beginThrow(user);
		try {
			return original.call(stack, level, user, timeLeft);
		} finally {
			TridentCriticalStrikes.endThrow(previous);
		}
	}
}
