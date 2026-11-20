package design.inherent.lootr_styled.profile;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.JsonDocuments;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileExporterTest {
  @TempDir Path root;

  @Test
  void exportIsAnEditableGeometryOnlyProfileWithSeparateOpenAndLootedMaterials() throws Exception {
    var configuration = new ClientConfiguration(root);
    byte[] model =
        "{\"textures\":{\"side\":\"lootr:block/barrel_side\",\"top\":\"lootr:block/barrel_top\"},\"elements\":[]}"
            .getBytes(StandardCharsets.UTF_8);
    Path profile = ProfileExporter.exportDefault(configuration, ContainerTargets.LEGACY, model);
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
    try (var files = Files.walk(profile)) {
      assertTrue(files.noneMatch(p -> p.toString().endsWith(".png")));
    }
    Files.writeString(profile.resolve("profile.json"), "edited");
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () -> ProfileExporter.exportDefault(configuration, ContainerTargets.LEGACY, model));
    assertEquals("edited", Files.readString(profile.resolve("profile.json")));
  }
}
