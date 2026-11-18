package lampas2overrides.customnamepermissions;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies the {@code /name other} permission fix whenever any Custom Name version is installed.
 *
 * <p>With {@code require_permissions} false, Custom Name's {@code CustomNameCommands#permissionCheck} returns an
 * always-true predicate for every node, so any player could rename any other player through {@code /name other}. The
 * mixin changes that for the {@code customname:other} node alone. It is separate from the space fix so that a moved member
 * in one feature cannot disable the other.
 *
 * <p>The gate is not version-bound: an upgrade must not reopen the hole. If {@code permissionCheck} or the Common
 * Permissions members move, the mixin fails startup ({@code required} with {@code defaultRequire: 1}), and the bridge throws
 * while Custom Name builds its command tree. A version other than {@link #AUDITED_VERSION} still gets the gate, with a WARN
 * that its permission contract has not been inspected.
 */
public final class CustomNamePermissionsMixinPlugin implements IMixinConfigPlugin {

	static final String MOD_ID = "eclipsescustomname";
	/** The release whose bytecode was inspected and whose descriptors the contract tests pin. */
	static final String AUDITED_VERSION = "0.4.4-26.2";
	private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/customnamepermissions");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = FabricLoader.getInstance()
			.getModContainer(MOD_ID)
			.map(container -> {
				String version = container.getMetadata().getVersion().getFriendlyString();
				if (isAudited(version)) {
					LOGGER.info("Enabled the Custom Name {} /name other operator gate", version);
				} else {
					LOGGER.warn("Custom Name {} is not the audited {} artifact; the /name other operator gate is applied, but its "
						+ "permissionCheck contract has not been inspected for this version", version, AUDITED_VERSION);
				}
				return true;
			})
			.orElse(false);
	}

	/** Whether {@code version} is the inspected release. Exact match only. */
	static boolean isAudited(String version) {
		return AUDITED_VERSION.equals(version);
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
