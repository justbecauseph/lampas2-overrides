package lampas2overrides.client.voxyreversez.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.Viewport;

/** The depth direction Voxy built this viewport's projection with. */
@Mixin(value = Viewport.class, remap = false)
public interface ViewportAccessor {

	@Accessor("properties")
	RenderProperties lampas2$getProperties();
}
