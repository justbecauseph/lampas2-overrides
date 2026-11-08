package lampas2overrides.lootrtickgate.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.Level;

import lampas2overrides.lootrtickgate.LootrTickGate;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.api.data.ILootrContainerInstance;

/**
 * Cancels Lootr's per-container tick on the server ticks where Lootr's own schedule does nothing with the result.
 *
 * <p>In Lootr 1.24.41.124, {@code ILootrAPI#handleInstanceTick} calls {@code LootrAPI.getData} before it checks
 * {@code getCurrentTicks() % (getTickDelay() + getRandomOffset()) == 0}. The lookup therefore runs for every loaded
 * container on every tick, while the schedule uses the result on one tick in 20 to 39. This injection sits at HEAD of
 * the static delegate, so it also covers the converted item-frame path in {@code lootrfastframes}, which calls the same
 * method. It repeats the null, null-level and client-side returns that come before the lookup, so the cancel applies only
 * to server-side containers, and it cancels exactly the ticks where Lootr would return after the lookup without using it.
 *
 * <p>The lookup's side effects move with it. Creating a container's in-memory store and syncing its name and position into
 * the saved record through {@code LootrInventoryStore#update} now happen on the container's scheduled ticks and on any
 * other call to {@code getData}. Opening a container still fetches its data immediately through {@code getInventory}.
 * A zero period is left to Lootr, which throws as it did before.
 */
@Mixin(value = LootrAPI.class, remap = false)
public abstract class LootrAPITickGateMixin {

	@Inject(method = "handleInstanceTick(Lnoobanidus/mods/lootr/common/api/data/ILootrContainerInstance;)V",
		at = @At("HEAD"), cancellable = true)
	private static void lampas2$skipUntilLootrTickDelay(ILootrContainerInstance instance, CallbackInfo ci) {
		if (instance == null) {
			return;
		}
		Level level = instance.getDataLevel();
		if (level == null || level.isClientSide()) {
			return;
		}
		if (LootrTickGate.skipsTick(LootrAPI.getCurrentTicks(), LootrAPI.getTickDelay(), instance.getRandomOffset())) {
			ci.cancel();
		}
	}
}
