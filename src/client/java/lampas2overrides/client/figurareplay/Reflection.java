package lampas2overrides.client.figurareplay;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Nullable reflective lookups into Figura and ReplayMod.
 *
 * <p>The bridge is compiled without either mod on the classpath, so every member it needs is
 * resolved by name at runtime. Lookups return {@code null} when a member is missing, which lets
 * {@link FiguraApi#bind()} and {@link ReplayModApi#bind()} report exactly which one moved instead
 * of failing with a stack trace three layers down. Invocation failures, on the other hand, mean
 * something we already resolved has since misbehaved, so they throw {@link BridgeException} and
 * take the whole bridge offline rather than being swallowed per-tick.
 */
final class Reflection {

	private Reflection() {
	}

	static Class<?> findClass(String name) {
		try {
			return Class.forName(name);
		} catch (Throwable ignored) {
			return null;
		}
	}

	static Method findMethod(Class<?> owner, String name, Class<?>... params) {
		if (owner == null) {
			return null;
		}
		try {
			return open(owner.getDeclaredMethod(name, params));
		} catch (Throwable ignored) {
			// Fall through: the member may be inherited rather than declared here.
		}
		try {
			return open(owner.getMethod(name, params));
		} catch (Throwable ignored) {
			return null;
		}
	}

	static Field findField(Class<?> owner, String name) {
		if (owner == null) {
			return null;
		}
		try {
			return open(owner.getDeclaredField(name));
		} catch (Throwable ignored) {
			// Fall through: the member may be inherited rather than declared here.
		}
		try {
			return open(owner.getField(name));
		} catch (Throwable ignored) {
			return null;
		}
	}

	static Object invoke(Method method, Object target, Object... args) {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw new BridgeException(method.getName() + " threw", e.getCause());
		} catch (Throwable t) {
			throw new BridgeException("cannot invoke " + method, t);
		}
	}

	static Object read(Field field, Object target) {
		try {
			return field.get(target);
		} catch (Throwable t) {
			throw new BridgeException("cannot read " + field, t);
		}
	}

	private static <T extends AccessibleObject> T open(T member) {
		try {
			member.setAccessible(true);
		} catch (Throwable ignored) {
			// Best effort; a public member works without it.
		}
		return member;
	}
}
