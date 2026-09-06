package lampas2overrides.trinketsdatafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.templates.Product;
import com.mojang.datafixers.types.templates.TypeTemplate;

/** Rebuilds the audited Trinkets V1460 item schema without its duplicate remainder. */
public final class TrinketsSchemaRepair {
	private TrinketsSchemaRepair() {
	}

	public static TypeTemplate repair(TypeTemplate result, TypeTemplate itemStack) {
		if (!(result instanceof Product outer)
			|| !(outer.g() instanceof Product nested)
			|| !(nested.g() instanceof Product originalTail)) {
			return result;
		}

		TypeTemplate original = originalTail.f();
		TypeTemplate items = DSL.list(itemStack);

		// The old wrapper puts the legacy/remainder branch first; typed Items/cosmetic fields are
		// rebuilt before that residual branch so nested item stacks reach vanilla DFU.
		TypeTemplate oldSlot = DSL.optional(DSL.compoundList(
			DSL.optionalFields("Items", items)));
		TypeTemplate oldData = DSL.optional(DSL.compoundList(DSL.and(
			oldSlot,
			DSL.optionalFields("Items", items),
			DSL.optionalFields("cosmetic", items))));
		TypeTemplate expectedOld = DSL.allWithRemainder(
			DSL.optional(DSL.field("cardinal_components",
				DSL.optionalFields("trinkets:trinkets", oldData))),
			DSL.optional(DSL.optionalFields("trinkets", oldData)),
			original);
		if (!result.equals(expectedOld)) {
			return result;
		}

		// Keep Items/cosmetic first, then accept the residual legacy map per entry and its scalar fallback.
		TypeTemplate slot = DSL.optionalFields("Items", items, "cosmetic", items);
		TypeTemplate legacySlots = DSL.compoundList(DSL.or(slot, DSL.remainder()));
		TypeTemplate entry = DSL.optionalFields("Items", items, "cosmetic", items,
			DSL.or(legacySlots, DSL.remainder()));
		TypeTemplate correctedData = DSL.or(
			DSL.compoundList(DSL.or(entry, DSL.remainder())),
			DSL.remainder());

		// Reuse the vanilla tail object exactly; only the two Trinkets field schemas change.
		return DSL.and(
			DSL.optional(DSL.field("cardinal_components",
				DSL.optionalFields("trinkets:trinkets", correctedData))),
			DSL.optional(DSL.field("trinkets", correctedData)),
			original);
	}
}
