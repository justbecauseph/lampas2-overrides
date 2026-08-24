package lampas2overrides.client.gravestones;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies the Gravestones overrides only to the affected Gravestones release.
 */
public final class GravestonesMixinPlugin implements IMixinConfigPlugin {

	private static final String AFFECTED_VERSION = "1.4.2";
	private static final String AFFECTED_VERSION_FULL = "1.4.2+26.2+A";

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = FabricLoader.getInstance()
			.getModContainer("gravestones")
			.map(container -> {
				String version = container.getMetadata().getVersion().getFriendlyString();
				return AFFECTED_VERSION.equals(version) || AFFECTED_VERSION_FULL.equals(version);
			})
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
