package lampas2overrides.betterlib.mixin;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.Lampas2Overrides;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;

/** Removes Better Lib's generated references to its two disabled demo professions after tag merge. */
@Mixin(TagLoader.class)
abstract class TagLoaderMixin {

	@Unique
	private static final Logger LAMPAS2_LOGGER =
		LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/better-lib");

	@Unique
	private static final Identifier ACQUIRABLE_JOB_SITE =
		Identifier.fromNamespaceAndPath("minecraft", "acquirable_job_site");

	@Shadow
	@Final
	private String directory;

	@Inject(method = "load", at = @At("RETURN"))
	private void lampas2$removeDisabledDemoProfessions(
		ResourceManager resourceManager,
		CallbackInfoReturnable<Map<Identifier, List<TagLoader.EntryWithSource>>> callback
	) {
		if (!"tags/point_of_interest_type".equals(directory)) {
			return;
		}

		List<TagLoader.EntryWithSource> entries = callback.getReturnValue().get(ACQUIRABLE_JOB_SITE);
		if (entries == null) {
			return;
		}

		int originalSize = entries.size();
		entries.removeIf(entry -> "better_lib".equals(entry.source())
			&& ("better_lib:andesite_worker".equals(entry.entry().toString())
				|| "better_lib:ore_trader".equals(entry.entry().toString())));

		if (entries.size() != originalSize) {
			LAMPAS2_LOGGER.info("Removed Better Lib's disabled demo professions from the acquirable job-site tag");
		}
	}
}
