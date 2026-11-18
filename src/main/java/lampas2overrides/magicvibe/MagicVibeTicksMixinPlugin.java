package lampas2overrides.magicvibe;

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

/** Applies the Magic Vibe Decorations tick cancel whenever any version of the mod is installed. */
public final class MagicVibeTicksMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/Magic Vibe ticks");

	private static final String INSPECTED_VERSION = "3.0.1";

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MagicVibeTicks.MOD_ID);
		apply = container.isPresent();
		container.ifPresent(mod -> {
			String version = mod.getMetadata().getVersion().getFriendlyString();
			LOGGER.info("Disabled scheduled block ticks for Magic Vibe Decorations {}", version);
			if (!INSPECTED_VERSION.equals(version)) {
				LOGGER.warn("Magic Vibe Decorations {} is not the inspected {}; its ticks are cancelled anyway, so re-check that no "
					+ "block relies on a scheduled tick for anything but crystal effects and particles", version, INSPECTED_VERSION);
			}
		});
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
