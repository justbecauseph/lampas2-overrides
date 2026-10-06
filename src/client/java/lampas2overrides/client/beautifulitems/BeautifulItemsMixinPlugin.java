package lampas2overrides.client.beautifulitems;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/** Gates each cosmetic item-model repair against its own exact installed mod artifact. */
public final class BeautifulItemsMixinPlugin implements IMixinConfigPlugin {

	private boolean enchantedBooksEnabled;
	private boolean potionsEnabled;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		enchantedBooksEnabled = BeautifulItemsProfiles.isInstalledTargetSupported(
			loader, BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET);
		potionsEnabled = BeautifulItemsProfiles.isInstalledTargetSupported(
			loader, BeautifulItemsProfiles.POTIONS_TARGET);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET.equals(targetClassName)) {
			return enchantedBooksEnabled;
		}
		if (BeautifulItemsProfiles.POTIONS_TARGET.equals(targetClassName)) {
			return potionsEnabled;
		}
		return false;
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
