package design.inherent.lootr_styled.profile;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.ResourceAccess;
import design.inherent.lootr_styled.resources.ResourceInputs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesTest {
  @TempDir Path temp;

  @Test
  void seedingPreservesSettingsAndInvalidDrafts() throws Exception {
    var files = new ClientConfiguration(temp);
    assertEquals(ClientSettings.DEFAULTS, files.load());
    var profile = temp.resolve("lootr_styled/default/profile.json");
    assertTrue(Files.isRegularFile(profile));
    Files.writeString(profile, "invalid user draft");
    files.load();
    assertEquals("invalid user draft", Files.readString(profile));
    Files.writeString(temp.resolve("lootr_styled/settings.json"), "invalid settings draft");
    assertThrows(IllegalArgumentException.class, files::load);
    assertEquals(
        "invalid settings draft", Files.readString(temp.resolve("lootr_styled/settings.json")));
  }

  @Test
  void saveRoundTripsRequestedIdentityWithoutChangingProfileDrafts() throws Exception {
    var files = new ClientConfiguration(temp);
    files.load();
    var request =
        new ClientSettings(
            true, ResourceId.parse("absent:kept"), ClientSettings.VANILLA, ClientSettings.Fresh.ON);
    files.save(request);
    assertEquals(request, files.load());
    try (var paths = Files.list(temp.resolve("lootr_styled"))) {
      assertTrue(paths.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
    }
  }

  @Test
  void anchoredReadsRefuseLeafAndParentSymlinks() throws Exception {
    Path root = Files.createDirectory(temp.resolve("root"));
    Path outside = Files.createDirectory(temp.resolve("outside"));
    Files.writeString(outside.resolve("secret.json"), "outside");
    Files.createSymbolicLink(root.resolve("leaf.json"), outside.resolve("secret.json"));
    Files.createSymbolicLink(root.resolve("parent"), outside);
    try (var local = LocalFiles.open(root)) {
      assertThrows(IOException.class, () -> local.read(Path.of("leaf.json"), 1024));
      assertThrows(IOException.class, () -> local.read(Path.of("parent/secret.json"), 1024));
      assertThrows(
          IllegalArgumentException.class,
          () -> local.read(Path.of("../outside/secret.json"), 1024));
    }
  }

  @Test
  void changedCapturedContentIsRefusedEvenWithSameLengthAndTimestamp() throws Exception {
    Path file = temp.resolve("profile.json");
    Files.writeString(file, "old");
    var time = Files.getLastModifiedTime(file);
    try (var local = LocalFiles.open(temp)) {
      assertEquals(
          "old",
          new String(
              local.read(Path.of("profile.json"), 1024).orElseThrow(),
              java.nio.charset.StandardCharsets.UTF_8));
      Files.writeString(file, "new");
      Files.setLastModifiedTime(file, time);
      assertThrows(IOException.class, local::validateCurrent);
    }
  }

  @Test
  void oneLinkedOrInvalidProfileDoesNotEraseAValidNeighbour() throws Exception {
    Path good = Files.createDirectory(temp.resolve("good"));
    Path bad = Files.createDirectory(temp.resolve("bad"));
    Files.writeString(good.resolve("profile.json"), "{\"format\":1}");
    Files.writeString(bad.resolve("profile.json"), "{\"format\":1,\"bad\":true}");
    Files.createSymbolicLink(temp.resolve("linked"), good);
    ResourceAccess none =
        new ResourceAccess() {
          @Override
          public Optional<Source> find(ResourceId id) {
            return Optional.empty();
          }

          @Override
          public Map<ResourceId, Source> list(String directory) {
            return Map.of();
          }
        };
    try (var local = LocalFiles.open(temp)) {
      var catalogue =
          ProfileCatalogue.discover(
              ContainerTargets.LEGACY, none, local, new ResourceInputs.Budget());
      assertTrue(catalogue.find(ResourceId.parse("lootr_styled_local:good")).isPresent());
      assertTrue(catalogue.find(ResourceId.parse("lootr_styled_local:bad")).isEmpty());
      assertTrue(catalogue.find(ResourceId.parse("lootr_styled_local:linked")).isEmpty());
      assertEquals(2, catalogue.diagnostics().size());
      local.validateCurrent();
    }
  }

  @Test
  void configuredRootCannotBeReplacedByASymlink() throws Exception {
    Path outside = Files.createDirectory(temp.resolve("outside"));
    Path link = temp.resolve("lootr_styled");
    Files.createSymbolicLink(link, outside);
    assertThrows(IOException.class, () -> LocalFiles.open(link));
    assertThrows(IOException.class, () -> new ClientConfiguration(temp).load());
    assertFalse(Files.exists(outside.resolve("settings.json")));
  }

  @Test
  void unsupportedProvidersRefuseInsteadOfUsingAnUnanchoredFallback() throws Exception {
    var zip = temp.resolve("test.zip");
    try (var fs =
        java.nio.file.FileSystems.newFileSystem(
            java.net.URI.create("jar:" + zip.toUri()), Map.of("create", "true"))) {
      assertThrows(IOException.class, () -> LocalFiles.open(fs.getPath("/")));
    }
  }
}
