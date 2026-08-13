package lampas2overrides.betterlib.mixin;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import lampas2overrides.Lampas2Overrides;
import lampas2overrides.betterlib.BorrowedFileSystem;

/** Prevents Better Lib from reopening and then closing Fabric Loader's shared mod-jar filesystem. */
@Mixin(targets = "com.reggarf.mods.better_lib.villager.json.JsonVillagerLoader", remap = false)
abstract class JsonVillagerLoaderMixin {

	@Unique
	private static final Logger LAMPAS2_LOGGER =
		LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/better-lib");

	@Redirect(
		method = "collectJsonFileNamesFromJarUrl",
		at = @At(
			value = "INVOKE",
			target = "Ljava/nio/file/FileSystems;newFileSystem(Ljava/net/URI;Ljava/util/Map;)Ljava/nio/file/FileSystem;"
		),
		remap = false
	)
	private static FileSystem lampas2$reuseExistingFileSystem(URI uri, Map<String, ?> environment)
		throws IOException {
		try {
			return FileSystems.newFileSystem(uri, environment);
		} catch (FileSystemAlreadyExistsException ignored) {
			LAMPAS2_LOGGER.info("Reusing Fabric Loader's existing mod-jar filesystem for Better Lib");
			return new BorrowedFileSystem(FileSystems.getFileSystem(uri));
		}
	}
}
