package lampas2overrides.client.justenoughfilters.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import lampas2overrides.client.justenoughfilters.JustEnoughFiltersMixinPlugin;
import lampas2overrides.client.justenoughfilters.JustEnoughFiltersProfiles;

/**
 * Triggers {@link JustEnoughFiltersMixinPlugin#postApply} on {@code mezz.jei.gui.ingredients.IngredientFilter} after
 * Just Enough Filters 3.2.0 (priority 1000) weaves {@code jef$applyFilters}.
 */
@Pseudo
@Mixin(targets = JustEnoughFiltersProfiles.INGREDIENT_FILTER_TARGET, priority = 1100, remap = false)
public abstract class IngredientFilterMixin {}
