package design.inherent.lootr_styled.profile;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.JsonDocuments;
import design.inherent.lootr_styled.resources.PresentationCompiler;
import design.inherent.lootr_styled.resources.ResourceAccess;
import design.inherent.lootr_styled.resources.TextureImages;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileExporterTest {
  @TempDir Path root;

  private static byte[] png() throws Exception {
    var image =
        new java.awt.image.BufferedImage(16, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0xff123456);
    var output = new ByteArrayOutputStream();
    assertTrue(javax.imageio.ImageIO.write(image, "png", output));
    return output.toByteArray();
  }

  private static ResourceAccess sources(Map<ResourceId, byte[]> assets) {
    return new ResourceAccess() {
      @Override
      public Optional<Source> find(ResourceId id) {
        return Optional.ofNullable(assets.get(id))
            .map(
                bytes ->
                    new Source(Origin.OTHER, "Test pack", () -> new ByteArrayInputStream(bytes)));
      }

      @Override
      public Map<ResourceId, Source> list(String directory) {
        return Map.of();
      }
    };
  }

  @Test
  void artworkExportCopiesActivePngsAndMetadataIntoAReimportableProfile() throws Exception {
    byte[] image = png(),
        metadata =
            "{\"animation\":{\"frametime\":3,\"frames\":[0,1]}}".getBytes(StandardCharsets.UTF_8);
    for (var targets : ContainerTargets.values()) {
      var configuration = new ProfileDirectory(root.resolve(targets.name()));
      var assets = new HashMap<ResourceId, byte[]>();
      targets.artwork().forEach(id -> assets.put(id, image));
      var first =
          targets.artwork().stream()
              .sorted(java.util.Comparator.comparing(ResourceId::toString))
              .findFirst()
              .orElseThrow();
      var meta = new ResourceId(first.namespace(), first.path() + ".mcmeta");
      assets.put(meta, metadata);
      var path = ProfileExporter.exportArtwork(configuration, targets, sources(assets));
      var profile =
          ProfileCodec.parse(
              ResourceId.parse("lootr_styled_local:artwork-copy"),
              Files.readAllBytes(path.resolve("profile.json")));
      assertEquals("Artwork copy", profile.title());
      assertTrue(profile.models().isEmpty());
      assertEquals(13, profile.artwork().size());
      for (var entry : assets.entrySet()) {
        var id = entry.getKey();
        assertArrayEquals(
            entry.getValue(),
            Files.readAllBytes(path.resolve(Path.of("assets", id.namespace(), id.path()))));
      }
      var provenance =
          JsonDocuments.object(Files.readAllBytes(path.resolve("SOURCES.json")))
              .getAsJsonObject("resources")
              .getAsJsonObject(first.toString());
      assertEquals("Test pack", provenance.get("source").getAsString());
      assertEquals(TextureImages.hash(image), provenance.get("sha256").getAsString());
      // No installed artwork is needed when this local copy is selected again.
      try (var local = LocalFiles.open(configuration.root())) {
        var settings =
            new ClientSettings(
                true, ClientSettings.VANILLA, profile.id(), ClientSettings.Fresh.OFF);
        var result =
            PresentationCompiler.compile(
                targets, settings, sources(Map.of()), local, bytes -> fail("Unexpected model"));
        assertEquals(13, result.resources().ids().size());
        for (var entry : assets.entrySet()) {
          try (var input = result.resources().open(entry.getKey()).orElseThrow()) {
            assertArrayEquals(entry.getValue(), input.readAllBytes());
          }
        }
        local.validateCurrent();
      }
      Files.writeString(path.resolve("profile.json"), "edited");
      var second = ProfileExporter.exportArtwork(configuration, targets, sources(assets));
      assertEquals("artwork-copy-2", second.getFileName().toString());
      assertEquals(
          "Artwork copy 2",
          JsonDocuments.object(Files.readAllBytes(second.resolve("profile.json")))
              .get("title")
              .getAsString());
      assertEquals("edited", Files.readString(path.resolve("profile.json")));
    }
  }

  @Test
  void missingCorruptOrInvalidAnimatedArtworkDoesNotPublishAPartialCopy() throws Exception {
    var configuration = new ProfileDirectory(root);
    var assets = new HashMap<ResourceId, byte[]>();
    byte[] image = png();
    ContainerTargets.LEGACY.artwork().forEach(id -> assets.put(id, image));
    var first = ContainerTargets.LEGACY.artwork().iterator().next();
    assets.remove(first);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ProfileExporter.exportArtwork(configuration, ContainerTargets.LEGACY, sources(assets)));
    assets.put(first, new byte[] {1, 2, 3});
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ProfileExporter.exportArtwork(configuration, ContainerTargets.LEGACY, sources(assets)));
    assets.put(first, image);
    assets.put(
        new ResourceId(first.namespace(), first.path() + ".mcmeta"),
        "{\"animation\":{\"frames\":[2]}}".getBytes(StandardCharsets.UTF_8));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ProfileExporter.exportArtwork(configuration, ContainerTargets.LEGACY, sources(assets)));
    assertFalse(Files.exists(configuration.root().resolve("artwork-copy")));
  }

  @Test
  void retiredArtworkExportLeavesItsPartialFilesUnadmittedAndPreservesTheNextCopy()
      throws Exception {
    var configuration = new ProfileDirectory(root);
    var assets = new HashMap<ResourceId, byte[]>();
    byte[] image = png();
    ContainerTargets.LEGACY.artwork().forEach(id -> assets.put(id, image));
    Path partial = configuration.root().resolve("artwork-copy");
    assertThrows(
        IllegalStateException.class,
        () ->
            ProfileExporter.exportArtwork(
                configuration,
                ContainerTargets.LEGACY,
                sources(assets),
                () -> {
                  if (Files.exists(partial.resolve("SOURCES.json")))
                    throw new IllegalStateException(
                        "Controlled resource retirement before publication");
                }));
    assertTrue(Files.isRegularFile(partial.resolve("SOURCES.json")));
    assertFalse(Files.exists(partial.resolve("profile.json")));
    try (var local = LocalFiles.open(configuration.root())) {
      var catalogue =
          ProfileCatalogue.discover(
              ContainerTargets.LEGACY,
              sources(Map.of()),
              local,
              new design.inherent.lootr_styled.resources.ResourceInputs.Budget());
      assertTrue(catalogue.find(ResourceId.parse("lootr_styled_local:artwork-copy")).isEmpty());
    }
    Path completed =
        ProfileExporter.exportArtwork(configuration, ContainerTargets.LEGACY, sources(assets));
    assertEquals("artwork-copy-2", completed.getFileName().toString());
    assertTrue(Files.isRegularFile(completed.resolve("profile.json")));
    assertFalse(Files.exists(partial.resolve("profile.json")));
  }

  @Test
  void auxiliaryBarrelArtworkRetainsItsModelBinding() throws Exception {
    var configuration = new ProfileDirectory(root);
    var assets = new HashMap<ResourceId, byte[]>();
    byte[] image = png();
    ContainerTargets.LEGACY.artwork().forEach(id -> assets.put(id, image));
    var target = ContainerTargets.LEGACY.barrels().getFirst().target();
    var auxiliary = ResourceId.parse("example:textures/block/front.png");
    assets.put(auxiliary, image);
    assets.put(
        target,
        "{\"textures\":{\"side\":\"example:block/front\"},\"elements\":[]}"
            .getBytes(StandardCharsets.UTF_8));
    Path path =
        ProfileExporter.exportArtwork(configuration, ContainerTargets.LEGACY, sources(assets));
    var id = ResourceId.parse("lootr_styled_local:artwork-copy");
    var profile = ProfileCodec.parse(id, Files.readAllBytes(path.resolve("profile.json")));
    assertEquals(1, profile.models().size());
    assertArrayEquals(
        image, Files.readAllBytes(path.resolve("assets/example/textures/block/front.png")));
    try (var local = LocalFiles.open(configuration.root())) {
      var settings = new ClientSettings(true, id, id, ClientSettings.Fresh.OFF);
      // Retain the native target as a valid slot, but supply no installed copy of its artwork.
      var result =
          PresentationCompiler.compile(
              ContainerTargets.LEGACY,
              settings,
              sources(Map.of(target, "{}".getBytes(StandardCharsets.UTF_8))),
              local,
              bytes -> {});
      var model =
          JsonDocuments.object(result.resources().open(target).orElseThrow().readAllBytes());
      String name = model.getAsJsonObject("textures").get("side").getAsString();
      assertTrue(name.startsWith("lootr_styled:block/generated/"));
      var texture = ResourceId.parse(name.replace(":", ":textures/") + ".png");
      try (var input = result.resources().open(texture).orElseThrow()) {
        assertArrayEquals(image, input.readAllBytes());
      }
      local.validateCurrent();
    }
  }

  @Test
  void exportIsAnEditableGeometryOnlyProfileWithSeparateOpenAndLootedMaterials() throws Exception {
    var configuration = new ProfileDirectory(root);
    Path profile = ProfileExporter.exportDefault(configuration, ContainerTargets.LEGACY);
    assertTrue(Files.isRegularFile(profile.resolve("profile.json")));
    assertEquals(
        4,
        JsonDocuments.object(Files.readAllBytes(profile.resolve("profile.json")))
            .getAsJsonObject("models")
            .size());
    var opened =
        JsonDocuments.object(
            Files.readAllBytes(
                profile.resolve(
                    "assets/lootr_styled_export/models/block/lootr_opened_barrel_open.json")));
    assertEquals(
        "lootr:block/opened_barrel_top_open",
        opened.getAsJsonObject("textures").get("top").getAsString());
    assertEquals(
        "lootr:block/opened_barrel_side",
        opened.getAsJsonObject("textures").get("side").getAsString());
    assertEquals(25, opened.getAsJsonArray("elements").size());
    assertFalse(opened.getAsJsonObject("textures").has("surface"));
    assertEquals(
        "lootr:block/opened_barrel_bottom",
        opened.getAsJsonObject("textures").get("bottom").getAsString());
    try (var files = Files.walk(profile)) {
      assertTrue(files.noneMatch(p -> p.toString().endsWith(".png")));
    }
    Files.writeString(profile.resolve("profile.json"), "edited");
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () -> ProfileExporter.exportDefault(configuration, ContainerTargets.LEGACY));
    assertEquals("edited", Files.readString(profile.resolve("profile.json")));
  }
}
