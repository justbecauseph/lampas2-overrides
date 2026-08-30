package lampas2overrides.stoneholm;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/** Applies Stoneholm data compatibility only when Underground Village is installed. */
public final class StoneholmMixinPlugin implements IMixinConfigPlugin {

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = FabricLoader.getInstance()
				.getModContainer("underground_village")
				.map(container -> StoneholmCompatibility.supportsVersion(container.getMetadata().getVersion().getFriendlyString()))
				.orElse(false);
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
