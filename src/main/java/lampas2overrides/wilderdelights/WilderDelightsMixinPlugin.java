package lampas2overrides.wilderdelights;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies the creative tab icon fix only to Wilder Delights 1.0.0-26.2.
 *
 * <p>Wilder Wild 4.3 changed {@code WWItems.SPLIT_COCONUT} from {@code Item} to {@code DeferredItem<Item>}.
 * Wilder Delights 1.0.0-26.2 still reads that field with an {@code Item} descriptor, so its creative tab icon
 * throws {@code NoSuchFieldError} when it renders.
 */
public final class WilderDelightsMixinPlugin implements IMixinConfigPlugin {

	static final String AFFECTED_VERSION = "1.0.0-26.2";

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = FabricLoader.getInstance()
			.getModContainer("wilderdelights")
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
