package lampas2overrides.customnamepermissions;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Reaches Custom Name's {@code customname:other} node and Common Permissions API's {@code require(node, level)} through
 * reflection, so the mod has no compile-time link to either. Both members are resolved on first use and cached. A missing
 * member throws {@link IllegalStateException} rather than falling back to a weaker check, and a null {@code OTHER} is
 * rejected, so it can never compare equal to a node.
 *
 * <p>Custom Name calls {@code permissionCheck} while it builds its command tree. That runs at startup and again on
 * {@code /reload} when the dispatcher is rebuilt, and the rebuild may happen on a worker thread. The cached fields are
 * therefore {@code volatile}. Each lookup is idempotent, so two threads that race on the first use only repeat it.
 */
public final class CustomNameOtherPermission {

	static final String PERMISSIONS_CLASS = "xyz.eclipseisoffline.eclipsescustomname.CustomNamePermissions";
	static final String OTHER_FIELD = "OTHER";
	static final String API_CLASS = "xyz.eclipseisoffline.commonpermissionsapi.api.CommonPermissions";
	static final String NODE_CLASS = "xyz.eclipseisoffline.commonpermissionsapi.api.CommonPermissionNode";
	static final String REQUIRE_METHOD = "require";

	private static volatile Object other;
	private static volatile MethodHandle require;

	private CustomNameOtherPermission() {
	}

	/** Whether {@code node} is Custom Name's {@code customname:other} node, compared by identity. */
	public static boolean isOther(Object node) {
		Object resolved = other;
		if (resolved == null) {
			resolved = resolveOther();
			other = resolved;
		}
		return node == resolved;
	}

	/**
	 * The predicate that Common Permissions builds for {@code node} at {@link PermissionLevel#GAMEMASTERS}: the same call
	 * Custom Name makes for every node when {@code require_permissions} is true. The predicate evaluates the source each
	 * time it is tested, so Brigadier sees the current permission of every command source.
	 */
	@SuppressWarnings("unchecked")
	public static Predicate<CommandSourceStack> requireGamemasters(Object node) {
		MethodHandle handle = require;
		if (handle == null) {
			handle = resolveRequire();
			require = handle;
		}
		try {
			return (Predicate<CommandSourceStack>) handle.invoke(node, PermissionLevel.GAMEMASTERS);
		} catch (Throwable failure) {
			throw new IllegalStateException("Common Permissions require(node, GAMEMASTERS) failed for "
					+ "Custom Name's /name other node", failure);
		}
	}

	private static Object resolveOther() {
		ClassLoader loader = CustomNameOtherPermission.class.getClassLoader();
		try {
			Class<?> permissions = Class.forName(PERMISSIONS_CLASS, false, loader);
			Class<?> node = Class.forName(NODE_CLASS, false, loader);
			Object resolved = MethodHandles.publicLookup().findStaticGetter(permissions, OTHER_FIELD, node).invoke();
			return Objects.requireNonNull(resolved, PERMISSIONS_CLASS + "." + OTHER_FIELD + " is null");
		} catch (Throwable failure) {
			throw new IllegalStateException("Custom Name " + PERMISSIONS_CLASS + "." + OTHER_FIELD + " is unavailable",
					failure);
		}
	}

	private static MethodHandle resolveRequire() {
		ClassLoader loader = CustomNameOtherPermission.class.getClassLoader();
		try {
			Class<?> api = Class.forName(API_CLASS, false, loader);
			Class<?> node = Class.forName(NODE_CLASS, false, loader);
			return MethodHandles.publicLookup().findStatic(api, REQUIRE_METHOD,
					MethodType.methodType(Predicate.class, node, PermissionLevel.class));
		} catch (ReflectiveOperationException | LinkageError failure) {
			throw new IllegalStateException("Common Permissions " + API_CLASS + "." + REQUIRE_METHOD + " is unavailable",
					failure);
		}
	}
}
