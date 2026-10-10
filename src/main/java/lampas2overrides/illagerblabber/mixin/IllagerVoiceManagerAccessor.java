package lampas2overrides.illagerblabber.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import lampas2overrides.illagerblabber.IllagerBlabberProfile;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

/** Reads a manager's entity so that replacement and unload can prove ownership by identity. */
@Pseudo
@Mixin(targets = IllagerBlabberProfile.MANAGER_TARGET, remap = false)
public interface IllagerVoiceManagerAccessor {

	@Accessor("illager")
	AbstractIllager getIllager();
}
