package lampas2overrides.client.justenoughfilters;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import lampas2overrides.client.justenoughfilters.JustEnoughFiltersProfiles.Status;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Gates the Just Enough Filters + JEI R8 local-slot reuse fix on the presence of {@code jei} and the exact audited
 * Just Enough Filters 3.2.0 artifact, and patches the woven target classes in {@link #postApply}.
 */
public final class JustEnoughFiltersMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/justenoughfilters");

	private boolean enabled;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		Status status = JustEnoughFiltersProfiles.status(loader);
		enabled = status == Status.SUPPORTED;
		if (status == Status.UNSUPPORTED) {
			LOGGER.warn(
				"JustEnoughFilters/JEI compatibility fix is only validated for Just Enough Filters {} ({}); "
					+ "installed Just Enough Filters is {}, leaving JustEnoughFilters' JEI mixins unpatched",
				JustEnoughFiltersProfiles.JUST_ENOUGH_FILTERS.version(),
				JustEnoughFiltersProfiles.JUST_ENOUGH_FILTERS.jarSha256(),
				installedVersion(loader, JustEnoughFiltersProfiles.JUST_ENOUGH_FILTERS.modId())
			);
		}
	}

	private static String installedVersion(FabricLoader loader, String modId) {
		return loader.getModContainer(modId)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("absent");
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return enabled;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (!enabled) {
			return;
		}
		JustEnoughFiltersBytecodePatcher.patch(targetClassName, targetClass);
	}
}
