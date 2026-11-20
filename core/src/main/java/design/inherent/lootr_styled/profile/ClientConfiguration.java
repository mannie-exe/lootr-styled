package design.inherent.lootr_styled.profile;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Local client preference ownership: seed once, preserve invalid files, save by atomic replacement.
 */
public final class ClientConfiguration {
  public static final String LOCAL_STARTER =
      """
      {
        "format": 1,
        "title": "Local inherited profile",
        "models": {},
        "artwork": {}
      }
      """;
  private final Path root;

  public ClientConfiguration(Path configDirectory) {
    root = configDirectory.resolve("lootr_styled");
  }

  public Path root() {
    return root;
  }

  public void seed() throws IOException {
    Files.createDirectories(root);
    directory(root);
    create(root.resolve("settings.json"), ClientSettings.STARTER);
    Path starter = root.resolve("default");
    if (Files.exists(starter, LinkOption.NOFOLLOW_LINKS)
        && !Files.isDirectory(starter, LinkOption.NOFOLLOW_LINKS)) return;
    Files.createDirectories(starter);
    directory(starter);
    create(starter.resolve("profile.json"), LOCAL_STARTER);
  }

  public ClientSettings load() throws IOException {
    return snapshot().settings();
  }

  public Snapshot snapshot() throws IOException {
    byte[] bytes = settingsBytes();
    return new Snapshot(ClientSettings.parse(bytes), hash(bytes));
  }

  private byte[] settingsBytes() throws IOException {
    seed();
    Path file = root.resolve("settings.json");
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Settings must be a regular file");
    try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
      byte[] bytes = input.readNBytes(16385);
      if (bytes.length > 16384) throw new IOException("Settings exceed 16 KiB");
      return bytes;
    }
  }

  /** Detect observed external changes before an explicit editor save. */
  public synchronized Snapshot save(ClientSettings settings, Snapshot expected) throws IOException {
    if (!hash(settingsBytes()).equals(expected.contentHash()))
      throw new IOException("Settings changed on disk; reopen before saving");
    save(settings);
    return snapshot();
  }

  public record Snapshot(ClientSettings settings, String contentHash) {
    public Snapshot {
      java.util.Objects.requireNonNull(settings);
      java.util.Objects.requireNonNull(contentHash);
    }
  }

  private static String hash(byte[] bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public void save(ClientSettings settings) throws IOException {
    directory(root);
    var json = new JsonObject();
    json.addProperty("format", 1);
    json.addProperty("enabled", settings.enabled());
    json.addProperty("models", settings.models().toString());
    json.addProperty("artwork", settings.artwork().toString());
    json.addProperty("freshObjects", settings.fresh().name());
    String text =
        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n";
    Path temporary = Files.createTempFile(root, "settings-", ".tmp");
    try {
      Files.writeString(
          temporary,
          text,
          StandardCharsets.UTF_8,
          StandardOpenOption.WRITE,
          LinkOption.NOFOLLOW_LINKS);
      Files.move(
          temporary,
          root.resolve("settings.json"),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void directory(Path path) throws IOException {
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Configuration directory cannot be a link or file");
  }

  private static void create(Path file, String text) throws IOException {
    try {
      Files.writeString(
          file,
          text,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW,
          LinkOption.NOFOLLOW_LINKS);
    } catch (java.nio.file.FileAlreadyExistsException ignored) {
      /* Existing user bytes, including invalid drafts, are preserved. */
    }
  }
}
