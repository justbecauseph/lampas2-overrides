package lampas2overrides.trinketsdatafix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.mojang.datafixers.DSL;
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
}
