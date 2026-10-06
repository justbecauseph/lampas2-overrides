package lampas2overrides.trinketsdatafix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.templates.Product;
import com.mojang.datafixers.types.templates.TypeTemplate;

final class TrinketsSchemaRepairTest {
	@Test void removesOnlyNestedTrinketsRemainder() {
		TypeTemplate items = DSL.list(DSL.remainder());
		TypeTemplate oldSlot = DSL.optional(DSL.compoundList(DSL.optionalFields("Items", items)));
		TypeTemplate oldData = DSL.optional(DSL.compoundList(DSL.and(oldSlot,
			DSL.optionalFields("Items", items), DSL.optionalFields("cosmetic", items))));
		TypeTemplate original = DSL.optionalFields("Inventory", items);
		TypeTemplate wrapped = DSL.allWithRemainder(
			DSL.optional(DSL.field("cardinal_components", DSL.optionalFields("trinkets:trinkets", oldData))),
			DSL.optional(DSL.optionalFields("trinkets", oldData)), original);
		assertNotSame(wrapped, TrinketsSchemaRepair.repair(wrapped, DSL.remainder()));
		TypeTemplate repaired = TrinketsSchemaRepair.repair(wrapped, DSL.remainder());
		assertSame(original, ((com.mojang.datafixers.types.templates.Product)
			((com.mojang.datafixers.types.templates.Product) repaired).g()).g());
		assertSame(repaired, TrinketsSchemaRepair.repair(repaired, DSL.remainder()));
	}

	@Test void rejectsWrongFieldShape() {
		TypeTemplate wrong = DSL.allWithRemainder(
			DSL.optional(DSL.field("other", DSL.remainder())),
			DSL.optional(DSL.optionalFields("trinkets", DSL.remainder())),
			DSL.optionalFields("Items", DSL.remainder()));
		assertSame(wrong, TrinketsSchemaRepair.repair(wrong, DSL.remainder()));
	}

	@Test void rejectsMalformedInnerAndCardinalShapes() {
		TypeTemplate item = DSL.remainder();
		TypeTemplate malformedInner = DSL.allWithRemainder(
			DSL.optional(DSL.field("cardinal_components", DSL.remainder())),
			DSL.optional(DSL.field("trinkets", DSL.remainder())),
			DSL.optionalFields("Inventory", DSL.list(item)));
		assertSame(malformedInner, TrinketsSchemaRepair.repair(malformedInner, item));

		TypeTemplate malformedCardinal = DSL.allWithRemainder(
			DSL.optional(DSL.field("cardinal_components_wrong", DSL.remainder())),
			DSL.optional(DSL.optionalFields("trinkets", DSL.remainder())),
			DSL.optionalFields("Inventory", DSL.list(item)));
		assertSame(malformedCardinal, TrinketsSchemaRepair.repair(malformedCardinal, item));
	}

	@Test void repairsOnlyTheExact411AndLeavesTheVanillaTailOwnedByVanilla() {
		TypeTemplate item = DSL.remainder();
		TypeTemplate items = DSL.list(item);
		TypeTemplate legacySlots = DSL.compoundList(
			DSL.allWithRemainder(DSL.field("Items", items)));
		TypeTemplate flatSlot = DSL.optionalFields("Items", items, "cosmetic", items);
		TypeTemplate currentData = DSL.optional(DSL.compoundList(DSL.or(legacySlots, flatSlot)));
		TypeTemplate original = DSL.optionalFields("Inventory", items);
		TypeTemplate current = DSL.and(
			DSL.optional(DSL.field("cardinal_components",
				DSL.optional(DSL.field("trinkets:trinkets", currentData)))),
			DSL.optional(DSL.field("trinkets", currentData)),
			original);

		TypeTemplate repaired = TrinketsSchemaRepair.repair(current, item,
			TrinketsSchemaRepair.Profile.V4_1_1);
		assertNotSame(current, repaired);
		assertSame(original, ((Product) ((Product) repaired).g()).g());
		assertSame(repaired, TrinketsSchemaRepair.repair(repaired, item,
			TrinketsSchemaRepair.Profile.V4_1_1));
		assertSame(current, TrinketsSchemaRepair.repair(current, item,
			TrinketsSchemaRepair.Profile.V4_1_0));
	}

	@Test void currentProfileRejectsNearbyShapes() {
		TypeTemplate item = DSL.remainder();
		TypeTemplate currentData = DSL.optional(DSL.compoundList(
			DSL.optionalFields("Items", DSL.list(item), "cosmetic", DSL.list(item))));
		TypeTemplate original = DSL.optionalFields("Inventory", DSL.list(item));
		TypeTemplate malformed = DSL.and(
			DSL.optional(DSL.field("cardinal_components", DSL.remainder())),
			DSL.optional(DSL.field("trinkets", currentData)),
			original);
		assertSame(malformed, TrinketsSchemaRepair.repair(malformed, item,
			TrinketsSchemaRepair.Profile.V4_1_1));
	}
}
