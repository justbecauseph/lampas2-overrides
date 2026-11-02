package lampas2overrides.wilderdelights.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Replaces Wilder Delights 1.0.0-26.2's read of {@code WWItems.SPLIT_COCONUT} in the creative tab icon supplier.
 *
 * <p>The vendor's {@code lambda$onInitialize$0} builds {@code new ItemStack(WWItems.SPLIT_COCONUT)} by reading the
 * field with the descriptor {@code Lnet/minecraft/world/item/Item;}. Wilder Wild 4.3 declares that field as
 * {@code DeferredItem<Item>}, so the GETSTATIC throws {@code NoSuchFieldError}. This redirect replaces the read with a
 * registry lookup of {@code wilderwild:split_coconut}, the item the field used to hold.
 *
 * <p>This field read is Wilder Delights' only runtime reference into Wilder Wild code; its datagen-only tag providers read
 * {@code WWBlockItemIds} and {@code WWItemIds}, which still match in Wilder Wild 4.3.3. The item id is a string, so Wilder
 * Wild is not a compile or test dependency.
 */
@Mixin(targets = "com.lilithelder.wilderdelights.WilderDelights", remap = false)
public abstract class WilderDelightsTabIconMixin {

	/**
	 * Returns the Wilder Wild split coconut by registry id in place of the removed {@code Item} field read.
	 * {@code BuiltInRegistries.ITEM} is a defaulted registry, so a missing id yields air rather than null.
	 */
	@Redirect(
		method = "lambda$onInitialize$0()Lnet/minecraft/world/item/ItemStack;",
		at = @At(
			value = "FIELD",
			target = "Lnet/frozenblock/wilderwild/registry/WWItems;"
				+ "SPLIT_COCONUT:Lnet/minecraft/world/item/Item;",
			opcode = Opcodes.GETSTATIC
		),
		remap = false
	)
	private static Item lampas2$splitCoconut() {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("wilderwild", "split_coconut"));
	}
}
