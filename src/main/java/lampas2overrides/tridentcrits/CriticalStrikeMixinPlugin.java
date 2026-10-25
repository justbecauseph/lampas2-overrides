package lampas2overrides.tridentcrits;

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
 * Applies the thrown-trident crit hooks whenever Critical Strike is installed, at any version. The member contract
 * {@code CriticalStrikeContractTest} checks is the inspected 1.0.6+26.2; other releases also activate but are not claimed as
 * inspected or tested. The hooks target vanilla classes, and {@link TridentCriticalStrikes} names Critical Strike only by
 * string, so a release that moved a member disables the bridge with one warning naming it.
 */
public final class CriticalStrikeMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/tridentcrits");
	private boolean applies;

	@Override
	public void onLoad(String mixinPackage) {
		Optional<ModContainer> criticalStrike = FabricLoader.getInstance().getModContainer(TridentCriticalStrikes.CRITICAL_STRIKE_MOD_ID);
		if (criticalStrike.isEmpty()) {
			return;
		}
		applies = true;
		LOGGER.info("Enabled Critical Strike {} crits for thrown tridents",
				criticalStrike.get().getMetadata().getVersion().getFriendlyString());
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return applies;
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
