package lampas2overrides.client.frozenwind;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;

/** Applies the wind lifecycle repair only to the inspected client artifact combination. */
public final class FrozenWindMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/frozen-wind");
	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		FrozenWindCompatibility.GateResult result = FrozenWindCompatibility.evaluate(FabricLoader.getInstance());
		apply = result.enabled();
		if (apply) {
			LOGGER.info("Enabled FrozenLib wind lifecycle compatibility for FrozenLib {} and Wilder Wild {}",
				FrozenWindCompatibility.FROZENLIB_VERSION, FrozenWindCompatibility.WILDERWILD_VERSION);
		} else {
			LOGGER.warn("Disabled FrozenLib wind lifecycle compatibility: {}", result.reason());
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return apply;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
