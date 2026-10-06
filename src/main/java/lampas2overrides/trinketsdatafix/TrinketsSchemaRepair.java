package lampas2overrides.trinketsdatafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.templates.Product;
import com.mojang.datafixers.types.templates.TypeTemplate;

/** Rebuilds the exact audited Trinkets V1460 schema wrapper for each supported artifact. */
public final class TrinketsSchemaRepair {
	public enum Profile {
		V4_1_0,
		V4_1_1
	}

	private TrinketsSchemaRepair() {
	}

	/** Retains the original 4.1.0 entry point for source and fixture compatibility. */
	public static TypeTemplate repair(TypeTemplate result, TypeTemplate itemStack) {
		return repair(result, itemStack, Profile.V4_1_0);
	}

	public static TypeTemplate repair(TypeTemplate result, TypeTemplate itemStack, Profile profile) {
		if (profile == null) return result;
		TypeTemplate original = profile == Profile.V4_1_0
			? legacyVanillaTail(result)
			: currentVanillaTail(result);
		if (original == null) return result;
		return switch (profile) {
			case V4_1_0 -> repair410(result, itemStack, original);
			case V4_1_1 -> repair411(result, itemStack, original);
		};
	}

	private static TypeTemplate legacyVanillaTail(TypeTemplate result) {
		if (!(result instanceof Product outer)
			|| !(outer.g() instanceof Product nested)
			|| !(nested.g() instanceof Product originalTail)) {
			return null;
		}
		return originalTail.f();
	}

	private static TypeTemplate currentVanillaTail(TypeTemplate result) {
		if (!(result instanceof Product outer) || !(outer.g() instanceof Product nested)) return null;
		return nested.g();
	}

	private static TypeTemplate repair410(TypeTemplate result, TypeTemplate itemStack, TypeTemplate original) {
		TypeTemplate items = DSL.list(itemStack);

		// Trinkets 4.1.0 wraps the typed fields in duplicate remainders. Keep this
		// profile's exact audited input contract independent from the newer schema.
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
		if (!result.equals(expectedOld)) return result;

		return DSL.and(
			DSL.optional(DSL.field("cardinal_components",
				DSL.optionalFields("trinkets:trinkets", correctedData(items)))),
			DSL.optional(DSL.field("trinkets", correctedData(items))),
			original);
	}

	private static TypeTemplate repair411(TypeTemplate result, TypeTemplate itemStack, TypeTemplate original) {
		TypeTemplate items = DSL.list(itemStack);
		TypeTemplate upstreamData = currentUpstreamData(items);
		TypeTemplate expectedCurrent = currentUpstreamWrapper(upstreamData, original);
		if (!result.equals(expectedCurrent)) return result;

		TypeTemplate correctedData = correctedData(items);
		TypeTemplate correctedCardinal = DSL.optional(DSL.field("cardinal_components",
			DSL.or(DSL.optionalFields("trinkets:trinkets", correctedData), DSL.remainder())));
		TypeTemplate correctedRoot = DSL.optional(DSL.field("trinkets", correctedData));
		// The third template is the exact vanilla V1460 tail. It owns the root
		// remainder, so do not add another root allWithRemainder wrapper here.
		return DSL.and(correctedCardinal, correctedRoot, original);
	}

	private static TypeTemplate currentUpstreamData(TypeTemplate items) {
		TypeTemplate legacySlots = DSL.compoundList(
			DSL.allWithRemainder(DSL.field("Items", items)));
		TypeTemplate flatSlot = DSL.optionalFields("Items", items, "cosmetic", items);
		return DSL.optional(DSL.compoundList(DSL.or(legacySlots, flatSlot)));
	}

	private static TypeTemplate currentUpstreamWrapper(TypeTemplate data, TypeTemplate original) {
		return DSL.and(
			DSL.optional(DSL.field("cardinal_components",
				DSL.optional(DSL.field("trinkets:trinkets", data)))),
			DSL.optional(DSL.field("trinkets", data)),
			original);
	}

	private static TypeTemplate correctedData(TypeTemplate items) {
		// A current flat slot stores Items/cosmetic beside metadata such as
		// hidden_slots. Legacy saves store group -> slot -> Items. Every typed
		// layer keeps its remaining fields; unknown representable values fall
		// through to remainder without dropping siblings.
		TypeTemplate flatSlot = DSL.optionalFields("Items", items, "cosmetic", items);
		TypeTemplate legacySlots = DSL.compoundList(DSL.or(flatSlot, DSL.remainder()));
		TypeTemplate entry = DSL.optionalFields("Items", items, "cosmetic", items,
			DSL.or(legacySlots, DSL.remainder()));
		return DSL.or(
			DSL.compoundList(DSL.or(entry, DSL.remainder())),
			DSL.remainder());
	}
}
