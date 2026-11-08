package lampas2overrides.lootrtickgate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Applies the Lootr tick-delay gate only to the exact audited Lootr 1.24.41.124 artifact.
 *
 * <p>Any other Lootr release, or no Lootr at all, leaves Lootr's tick path untouched.
 */
public final class LootrTickGateMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/Lootr tick gate");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		Optional<ModContainer> container = loader.getModContainer(LootrTickGateProfile.MOD_ID);
		if (container.isEmpty()) {
			apply = false;
			return;
		}
		apply = LootrTickGateProfile.isInstalledSupported(loader);
		if (apply) {
			LOGGER.info("Enabled version-gated Lootr 1.24.41.124 tick-delay gate");
		} else {
			LOGGER.warn("Lootr {} is installed but is not the audited 1.24.41.124 artifact; the tick-delay gate remains disabled",
				LootrTickGateProfile.installedVersion(container.get()));
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
