package lampas2overrides.lootrfastframes;

import noobanidus.mods.lootr.common.entity.LootrItemFrame;

/** State bridge added to Fast Item Frames' block entity by mixin. */
public interface LootrFastItemFrame {

	boolean lampas2$isLootrFrame();

	void lampas2$initializeFrom(LootrItemFrame source);

	void lampas2$markOpened();
}
