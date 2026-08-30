package lampas2overrides.resourcefix.mixin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.resourcefix.ResourcePatchResolver;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Intercepts Fabric's ModNioPackResources to inject virtual resource replacements
 * for known-defective mod resources without touching mod archives on disk.
 */
@Mixin(value = ModNioPackResources.class, remap = false)
abstract class ModNioPackResourcesMixin {

	@Unique
	private static final Logger LAMPAS2_LOGGER = LoggerFactory.getLogger("lampas2-overrides/resource-fix");

	@Shadow
	@Final
	private ModContainer mod;

	@Shadow
	private Path getPath(String filename) {
		throw new AssertionError();
	}

	@Shadow
	private static String getFilename(PackType type, Identifier id) {
		throw new AssertionError();
	}

	@Unique
	private String lampas2$resolveRelativePath(Path path, String fallbackFilename) {
		if (this.mod != null && path != null) {
			for (Path root : this.mod.getRootPaths()) {
				if (path.startsWith(root)) {
					return root.relativize(path).toString().replace('\\', '/');
				}
			}
			String s = path.toString().replace('\\', '/');
			if (s.startsWith("/")) {
				s = s.substring(1);
			}
			return s;
		}
		return fallbackFilename;
	}

	@Inject(method = "openFile", at = @At("HEAD"), cancellable = true, remap = false)
	private void lampas2$patchOpenFile(String filename, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
		Path path = this.getPath(filename);
		String fullPath = lampas2$resolveRelativePath(path, filename);
		String normalized = ResourcePatchResolver.normalizePath(fullPath);
		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(this.mod, normalized, () -> {
			if (path != null && Files.isRegularFile(path)) {
				return Files.newInputStream(path);
			}
			return null;
		});
		if (patched != null) {
			cir.setReturnValue(patched);
		}
	}

	@Inject(method = "getMetadataSection", at = @At("HEAD"), cancellable = true, remap = false)
	private <T> void lampas2$patchGetMetadataSection(
		net.minecraft.server.packs.metadata.MetadataSectionType<T> type,
		CallbackInfoReturnable<T> cir
	) {
		Path path = this.getPath("pack.mcmeta");
		String fullPath = lampas2$resolveRelativePath(path, "pack.mcmeta");
		String modId = this.mod != null ? this.mod.getMetadata().getId() : "unknown";
		String normalized = ResourcePatchResolver.normalizePath(fullPath);
		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(this.mod, normalized, () -> {
			if (path != null && Files.isRegularFile(path)) {
				return Files.newInputStream(path);
			}
			return null;
		});
		if (patched != null) {
			try (InputStream is = patched.get()) {
				if (is != null) {
					net.minecraft.server.packs.resources.ResourceMetadata meta =
						net.minecraft.server.packs.resources.ResourceMetadata.fromJsonStream(is);
					cir.setReturnValue(meta.getSection(type).orElse(null));
				}
			} catch (Exception e) {
				LAMPAS2_LOGGER.warn("Failed to parse patched pack.mcmeta for mod {}",
					this.mod != null ? this.mod.getMetadata().getId() : "unknown", e);
			}
		}
	}

	@Inject(method = "getResource", at = @At("HEAD"), cancellable = true, remap = false)
	private void lampas2$patchGetResource(PackType type, Identifier id, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
		String resourcePath = type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath();
		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(this.mod, resourcePath, () -> {
			Path path = this.getPath(getFilename(type, id));
			if (path != null && Files.isRegularFile(path)) {
				return Files.newInputStream(path);
			}
			return null;
		});
		if (patched != null) {
			cir.setReturnValue(patched);
		}
	}

	@ModifyVariable(method = "listResources", at = @At("HEAD"), argsOnly = true, remap = false)
	private PackResources.ResourceOutput lampas2$wrapResourceOutput(
		PackResources.ResourceOutput visitor,
		PackType type,
		String namespace,
		String path
	) {
		if (visitor == null || this.mod == null) {
			return visitor;
		}
		return (id, supplier) -> {
			String resourcePath = type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath();
			IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(this.mod, resourcePath, supplier);
			visitor.accept(id, patched != null ? patched : supplier);
		};
	}
}
