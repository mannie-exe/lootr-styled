package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientFileSettings;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;

/** Bounded, verification-only copies of loaded archives and public class-resource witnesses. */
final class RuntimeArtifacts {
  private static final long ARCHIVE_LIMIT = 64L * 1024 * 1024, CLASS_LIMIT = 4L * 1024 * 1024;
  private static final Map<String, List<Class<?>>> WITNESSES =
      Map.of(
          "lootr_styled", List.of(ResourceId.class, ClientRuntime.class, StylePreferences.class),
          "reform",
              List.of(
                  ClientFileSettings.class, ClientEditingWorkflow.class, ClientGraphicHost.class));

  private RuntimeArtifacts() {}

  static JsonObject capture(Path fixture, Map<String, Path> roots) throws Exception {
    if (!roots.keySet().equals(Set.of("lootr_styled", "reform")))
      throw new IllegalArgumentException("Expected exactly the Styled and re:form loader roots");
    var directory = fixture.resolve("runtime-artifacts");
    if (Files.isSymbolicLink(directory)) throw new IllegalStateException("Symbolic runtime output");
    Files.createDirectories(directory);
    var result = new JsonObject();
    for (var entry : roots.entrySet()) {
      var actual = entry.getValue().toRealPath();
      var before = Files.readAttributes(actual, BasicFileAttributes.class);
      if (!before.isRegularFile())
        throw new IllegalStateException("Expected packaged runtime archive: " + actual);
      if (before.size() <= 0 || before.size() > ARCHIVE_LIMIT)
        throw new IllegalStateException("Runtime archive exceeds fixture allowance: " + actual);
      String relative = "runtime-artifacts/" + entry.getKey() + ".jar";
      var copy = fixture.resolve(relative);
      String digest;
      try (var input = Files.newInputStream(actual);
          var output =
              Files.newOutputStream(
                  copy, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        digest = hash(input, output, ARCHIVE_LIMIT);
      }
      var after = Files.readAttributes(actual, BasicFileAttributes.class);
      if (before.size() != after.size()
          || before.size() != Files.size(copy)
          || !before.lastModifiedTime().equals(after.lastModifiedTime())
          || !Objects.equals(before.fileKey(), after.fileKey()))
        throw new IllegalStateException("Runtime archive changed during capture: " + actual);
      var facts = new JsonObject();
      facts.addProperty("path", actual.toString());
      facts.addProperty("copy", relative);
      facts.addProperty("bytes", before.size());
      facts.addProperty("sha256", digest);
      var witnesses = new JsonArray();
      try (var zip = new ZipFile(copy.toFile())) {
        for (var type : WITNESSES.get(entry.getKey())) {
          String member = type.getName().replace('.', '/') + ".class";
          var item = zip.getEntry(member);
          if (item == null) throw new IllegalStateException("Runtime archive lacks " + member);
          String packaged;
          try (var input = zip.getInputStream(item)) {
            packaged = hash(input, null, CLASS_LIMIT);
          }
          var resource = type.getResource("/" + member);
          if (resource == null)
            throw new IllegalStateException("Missing loaded resource: " + member);
          try (var input = resource.openStream()) {
            if (!packaged.equals(hash(input, null, CLASS_LIMIT)))
              throw new IllegalStateException(
                  "Class resource differs from loaded archive: " + member);
          }
          var witness = new JsonObject();
          witness.addProperty("member", member);
          witness.addProperty("sha256", packaged);
          witness.addProperty("resource", resource.toExternalForm());
          witness.addProperty("resourceMatchesArchive", true);
          witnesses.add(witness);
        }
      }
      facts.add("witnesses", witnesses);
      result.add(entry.getKey(), facts);
    }
    return result;
  }

  private static String hash(InputStream input, OutputStream copy, long limit) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    var buffer = new byte[8192];
    long size = 0;
    for (int count; (count = input.read(buffer)) != -1; ) {
      size += count;
      if (size > limit) throw new IllegalStateException("Runtime identity input exceeds allowance");
      digest.update(buffer, 0, count);
      if (copy != null) copy.write(buffer, 0, count);
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
