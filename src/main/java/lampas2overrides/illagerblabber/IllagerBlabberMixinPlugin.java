package lampas2overrides.illagerblabber;

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

/** Applies the IllagerBlabber registry fixes only to the exact audited 1.1.0 artifact. */
public final class IllagerBlabberMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/IllagerBlabber");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		Optional<ModContainer> container = loader.getModContainer(IllagerBlabberProfile.MOD_ID);
		if (container.isEmpty()) {
			apply = false;
			return;
		}
		apply = IllagerBlabberProfile.isInstalledSupported(loader);
		if (apply) {
			LOGGER.info("Enabled version-gated IllagerBlabber 1.1.0 registry fixes");
		} else {
			LOGGER.warn("IllagerBlabber {} is installed but is not the audited 1.1.0 artifact; registry fixes remain disabled",
				IllagerBlabberProfile.installedVersion(container.get()));
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
