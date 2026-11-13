package lampas2overrides.fwaentityculling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/** The canceller's decision, with the Fabric Loader check passed in as a supplier. */
class FwaCullTaskMixinCancellerTest {

	/** The vendor mixin's name as fwa 1.3.4 ships it, spelled out here rather than read from the profile constant. */
	private static final String VENDOR_MIXIN = "fr.madu59.fwa.mixin.client.compat.entityculling.CullTaskMixin";

	@Test
	void otherMixinsAreNotCancelledAndTheSupplierIsNotRead() {
		assertFalse(FwaCullTaskMixinCanceller.shouldCancel("some.other.Mixin", () -> {
			fail("the supplier must not be called for another mixin");
			return false;
		}));
	}

	@Test
	void vendorMixinIsCancelledWhenThePairIsActive() {
		assertTrue(FwaCullTaskMixinCanceller.shouldCancel(VENDOR_MIXIN, () -> true));
	}

	@Test
	void vendorMixinIsKeptWhenThePairIsNotActive() {
		assertFalse(FwaCullTaskMixinCanceller.shouldCancel(VENDOR_MIXIN, () -> false));
	}
}
