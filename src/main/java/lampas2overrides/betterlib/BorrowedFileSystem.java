package lampas2overrides.betterlib;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

/**
 * A close-shield around a filesystem owned by Fabric Loader.
 *
 * <p>Paths may safely come from the delegate; the shield exists only so Better Lib's
 * try-with-resources block cannot close a shared ZIP filesystem.
 */
public final class BorrowedFileSystem extends FileSystem {

	private final FileSystem delegate;

	public BorrowedFileSystem(FileSystem delegate) {
		this.delegate = delegate;
	}

	@Override
	public FileSystemProvider provider() {
		return delegate.provider();
	}

	@Override
	public void close() {
		// The delegate is owned by Fabric Loader, not Better Lib.
	}

	@Override
	public boolean isOpen() {
		return delegate.isOpen();
	}

	@Override
	public boolean isReadOnly() {
		return delegate.isReadOnly();
	}

	@Override
	public String getSeparator() {
		return delegate.getSeparator();
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return delegate.getRootDirectories();
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return delegate.getFileStores();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return delegate.supportedFileAttributeViews();
	}

	@Override
	public Path getPath(String first, String... more) {
		return delegate.getPath(first, more);
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		return delegate.getPathMatcher(syntaxAndPattern);
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		return delegate.getUserPrincipalLookupService();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		return delegate.newWatchService();
	}
}
