package lampas2overrides.client.visualworkbench;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class VisualWorkbenchTagFix {

	public static final String VISUAL_WORKBENCH_NAMESPACE = "visualworkbench";
	public static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/visual-workbench");

	private static final MethodHandle BIND_TAGS_HANDLE;

	static {
		try {
			Method method = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
			method.setAccessible(true);
			BIND_TAGS_HANDLE = MethodHandles.lookup().unreflect(method);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to resolve Holder.Reference#bindTags", e);
		}
	}

	private VisualWorkbenchTagFix() {
	}

	public static boolean isVisualWorkbenchTarget(Identifier id) {
		return id != null && VISUAL_WORKBENCH_NAMESPACE.equals(id.getNamespace());
	}

	public static <T> boolean needsRebind(Set<T> sourceTags, Set<T> targetTags) {
		return !Objects.equals(sourceTags, targetTags);
	}

	public static void bindTags(Holder.Reference<Block> holder, Collection<TagKey<Block>> tags) {
		try {
			BIND_TAGS_HANDLE.invoke(holder, tags);
		} catch (Throwable t) {
			throw new IllegalStateException("Failed to bind tags on holder " + holder, t);
		}
	}

	public static void rebindTags(Block from, Block to, Identifier targetId) {
		Set<TagKey<Block>> sourceTags = from.builtInRegistryHolder()
				.tags()
				.collect(Collectors.toSet());
		Set<TagKey<Block>> targetTags = to.builtInRegistryHolder()
				.tags()
				.collect(Collectors.toSet());

		if (needsRebind(sourceTags, targetTags)) {
			if (!targetTags.isEmpty()) {
				LOGGER.debug("Rebinding tags for {}: {} old tag(s) -> {} current tag(s)",
						targetId, targetTags.size(), sourceTags.size());
			}
			bindTags(to.builtInRegistryHolder(), sourceTags);
		}
	}
}
