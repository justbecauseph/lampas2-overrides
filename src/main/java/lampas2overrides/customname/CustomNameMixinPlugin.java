package lampas2overrides.customname;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies the player-name space fix only to Custom Name 0.4.4-26.2.
 *
 * <p>Custom Name 0.4.4-26.2 passes {@code operatorsBypassRestrictions} directly as the
 * {@code spaceAllowed} flag inside {@code CustomNameUtil#playerNameArgumentToComponent}.
 * When restrictions are not bypassed, this silently truncates multi-word names at the first
 * space. The mixin patches only that one call-site argument; all other restriction checks
 * (blacklist, max length, permissions, name groups) remain entirely unchanged.
 *
 * <p>This is version-gated exactly like NameTagUpgradeMixinPlugin: any future version that
 * changes the affected method signature will simply skip the patch rather than
 * double-applying it.
 */
public final class CustomNameMixinPlugin implements IMixinConfigPlugin {

	static final String AFFECTED_VERSION = "0.4.4-26.2";

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = FabricLoader.getInstance()
			.getModContainer("eclipsescustomname")
			.map(container -> AFFECTED_VERSION.equals(container.getMetadata().getVersion().getFriendlyString()))
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
