package design.inherent.lootr_styled.profile;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reload-scoped local snapshots. Not a sandbox against a hostile process with the user's access.
 */
public final class LocalFiles implements AutoCloseable {
  private final Path rootPath;
  private final Stamp rootIdentity;
  private final DirectoryStream<Path> stream;
  private final SecureDirectoryStream<Path> secure;
  private final Map<Path, byte[]> reads = new HashMap<>();
  private List<String> profileNames;
  private Stamp listingIdentity;
  private boolean closed;

  private LocalFiles(
      Path rootPath,
      Stamp rootIdentity,
      DirectoryStream<Path> stream,
      SecureDirectoryStream<Path> secure) {
    this.rootPath = rootPath;
    this.rootIdentity = rootIdentity;
    this.stream = stream;
    this.secure = secure;
  }

  public static LocalFiles open(Path directory) throws IOException {
    if (!directory.getFileSystem().provider().getScheme().equals("file"))
      throw new IOException("Unsupported local profile filesystem");
    if (Files.isSymbolicLink(directory))
      throw new IOException("Local profile root is a symbolic link");
    Path real = directory.toRealPath();
    var attributes =
        Files.readAttributes(real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isDirectory()) throw new IOException("Local profile root must be a directory");
    DirectoryStream<Path> stream = Files.newDirectoryStream(real);
    SecureDirectoryStream<Path> secure =
        stream instanceof SecureDirectoryStream<Path> anchored ? anchored : null;
    return new LocalFiles(real, stamp(attributes), stream, secure);
  }

  public List<String> profiles() throws IOException {
    validateRoot();
    if (profileNames != null) {
      validateListing();
      return profileNames;
    }
    listingIdentity = rootStamp();
    var result = new ArrayList<String>();
    int count = 0;
    for (var path : stream) {
      if (++count > 256) throw new IOException("Local profile root entry budget exceeded");
      String name = path.getFileName().toString();
      if (!name.matches("[a-z0-9_-]{1,64}")) continue;
      var attrs = attributes(Path.of(name));
      // Keep linked names in the catalogue walk so their individual descriptor read is refused.
      if (attrs.isDirectory() || attrs.isSymbolicLink()) result.add(name);
      if (result.size() > 64) throw new IOException("Local profile catalogue exceeds 64 profiles");
    }
    validateListing();
    profileNames = result.stream().sorted().toList();
    return profileNames;
  }

  public Optional<byte[]> read(Path relative, int maxBytes) throws IOException {
    return read(relative, maxBytes, true);
  }

  private Optional<byte[]> read(Path relative, int maxBytes, boolean record) throws IOException {
    validate(relative);
    if (maxBytes < 0 || maxBytes > 2 * 1024 * 1024)
      throw new IllegalArgumentException("Invalid local read budget");
    validateRoot();
    Optional<byte[]> captured =
        secure == null ? portableRead(relative, maxBytes) : anchoredRead(relative, maxBytes);
    if (record && captured.isPresent()) {
      if (reads.size() >= 256 && !reads.containsKey(relative))
        throw new IOException("Local input count exceeded");
      byte[] value = digest(captured.orElseThrow());
      var previous = reads.putIfAbsent(relative, value);
      if (previous != null && !java.util.Arrays.equals(previous, value))
        throw new IOException("Local input generation changed");
    }
    return captured;
  }

  private Optional<byte[]> anchoredRead(Path relative, int maxBytes) throws IOException {
    var children = new ArrayList<SecureDirectoryStream<Path>>();
    SecureDirectoryStream<Path> directory = secure;
    try {
      for (int i = 0; i < relative.getNameCount() - 1; i++) {
        directory = directory.newDirectoryStream(relative.getName(i), LinkOption.NOFOLLOW_LINKS);
        children.add(directory);
      }
      var leaf = relative.getFileName();
      var view =
          directory.getFileAttributeView(
              leaf, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
      var before = view.readAttributes();
      regular(before, maxBytes);
      byte[] bytes;
      try (var input =
          Channels.newInputStream(
              directory.newByteChannel(
                  leaf, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)))) {
        bytes = input.readNBytes(maxBytes + 1);
      }
      if (bytes.length > maxBytes || !stamp(before).equals(stamp(view.readAttributes())))
        throw new IOException("Local file changed during capture");
      return Optional.of(bytes);
    } catch (NoSuchFileException ex) {
      return Optional.empty();
    } finally {
      for (int i = children.size() - 1; i >= 0; i--) children.get(i).close();
    }
  }

  private Optional<byte[]> portableRead(Path relative, int maxBytes) throws IOException {
    try {
      var before = chain(relative);
      regular(
          Files.readAttributes(
              rootPath.resolve(relative), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS),
          maxBytes);
      byte[] bytes;
      try (var input =
          Files.newInputStream(
              rootPath.resolve(relative), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
        bytes = input.readNBytes(maxBytes + 1);
      }
      if (bytes.length > maxBytes || !before.equals(chain(relative)))
        throw new IOException("Local file changed during capture");
      return Optional.of(bytes);
    } catch (NoSuchFileException ex) {
      return Optional.empty();
    }
  }

  private List<Stamp> chain(Path relative) throws IOException {
    validateRoot();
    var result = new ArrayList<Stamp>();
    Path current = rootPath;
    for (int i = 0; i < relative.getNameCount(); i++) {
      current = current.resolve(relative.getName(i));
      var attrs =
          Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (attrs.isSymbolicLink() || i < relative.getNameCount() - 1 && !attrs.isDirectory())
        throw new IOException("Linked or invalid local resource path");
      result.add(stamp(attrs));
    }
    if (!current.toRealPath().startsWith(rootPath))
      throw new IOException("Local resource escaped its root");
    return result;
  }

  private BasicFileAttributes attributes(Path relative) throws IOException {
    return secure == null
        ? Files.readAttributes(
            rootPath.resolve(relative), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
        : secure
            .getFileAttributeView(relative, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            .readAttributes();
  }

  private void validateRoot() throws IOException {
    if (closed) throw new IOException("Local profile snapshot is closed");
    var current =
        Files.readAttributes(rootPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!current.isDirectory()
        || !java.util.Objects.equals(current.fileKey(), rootIdentity.fileKey()))
      throw new IOException("Local profile root changed");
  }

  private Stamp rootStamp() throws IOException {
    return stamp(
        Files.readAttributes(rootPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
  }

  private void validateListing() throws IOException {
    if (!listingIdentity.equals(rootStamp()))
      throw new IOException("Local profile catalogue changed during preparation");
  }

  private static void regular(BasicFileAttributes attrs, int maxBytes) throws IOException {
    if (!attrs.isRegularFile() || attrs.size() > maxBytes)
      throw new IOException("Unsupported or oversized local file");
  }

  public void validateCurrent() throws IOException {
    validateRoot();
    if (profileNames != null) validateListing();
    for (var entry : reads.entrySet()) {
      var bytes = read(entry.getKey(), 2 * 1024 * 1024, false);
      if (bytes.isEmpty()
          || !java.util.Arrays.equals(entry.getValue(), digest(bytes.orElseThrow())))
        throw new IOException("Local profile changed during preparation");
    }
    if (profileNames != null) validateListing();
  }

  private static Stamp stamp(BasicFileAttributes value) {
    return new Stamp(value.fileKey(), value.size(), value.lastModifiedTime());
  }

  private static byte[] digest(byte[] bytes) {
    try {
      return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static void validate(Path path) {
    if (path.isAbsolute() || path.getNameCount() > 32)
      throw new IllegalArgumentException("Invalid local resource path");
    for (var part : path) {
      String name = part.toString();
      if (!name.matches("[a-z0-9_.-]+") || name.equals(".") || name.equals(".."))
        throw new IllegalArgumentException("Escaping local resource path");
    }
  }

  @Override
  public void close() throws IOException {
    closed = true;
    stream.close();
  }

  private record Stamp(Object fileKey, long size, FileTime modified) {}
}
