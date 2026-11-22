package design.inherent.lootr_styled.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Local profile directory and create-if-absent starter; settings belong to re:form. */
public final class ProfileDirectory {
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

  public ProfileDirectory(Path configDirectory) {
    root = configDirectory.resolve("lootr_styled");
  }

  public Path root() {
    return root;
  }

  public void seed() throws IOException {
    Files.createDirectories(root);
    directory(root);
    Path starter = root.resolve("default");
    if (Files.exists(starter, LinkOption.NOFOLLOW_LINKS)
        && !Files.isDirectory(starter, LinkOption.NOFOLLOW_LINKS)) return;
    Files.createDirectories(starter);
    directory(starter);
    create(starter.resolve("profile.json"), LOCAL_STARTER);
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
