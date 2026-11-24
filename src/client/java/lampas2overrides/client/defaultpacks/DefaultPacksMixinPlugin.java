package lampas2overrides.client.defaultpacks;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies the default-pack mixin whenever Fabric API's resource loader is installed. It names Fabric API only through
 * the mixin target, so a client without Fabric API skips the feature instead of failing on a missing class.
 */
public final class DefaultPacksMixinPlugin implements IMixinConfigPlugin {

	static final String RESOURCE_LOADER_MOD_ID = "fabric-resource-loader-v1";

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = FabricLoader.getInstance().isModLoaded(RESOURCE_LOADER_MOD_ID);
		if (apply) {
			DefaultEnabledPacks.LOGGER.info("Enabled default selection for {} built-in resource packs", DefaultEnabledPacks.PACK_IDS.size());
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
