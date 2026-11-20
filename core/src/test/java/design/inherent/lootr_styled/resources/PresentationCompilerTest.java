package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PresentationCompilerTest {
  private static final ContainerTargets TARGETS = ContainerTargets.LEGACY;
  private static final String CUBE = "{\"elements\":[{\"from\":[1,2,3],\"to\":[14,15,16]}]}";

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] png() throws Exception {
    var output = new ByteArrayOutputStream();
    var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0xff102030);
    assertTrue(javax.imageio.ImageIO.write(image, "png", output));
    return output.toByteArray();
  }

  private static Memory inputs() {
    var memory = new Memory();
    memory.put(BarrelCompiler.DEFAULT_MODEL, bytes(CUBE), ResourceAccess.Origin.OTHER);
    for (var barrel : TARGETS.barrels()) {
      memory.put(barrel.target(), bytes("{}"), ResourceAccess.Origin.LOOTR);
      memory.put(barrel.vanilla(), bytes(CUBE), ResourceAccess.Origin.MINECRAFT);
    }
    return memory;
  }

  private static PresentationCompiler.Result compile(Memory source, ClientSettings settings) {
    return PresentationCompiler.compile(
        TARGETS,
        settings,
        source,
        null,
        bytes -> BlockModels.validate(JsonDocuments.object(bytes)));
  }

  private static ClientSettings settings(String model, String artwork) {
    return new ClientSettings(
        true, ResourceId.parse(model), ResourceId.parse(artwork), ClientSettings.Fresh.OFF);
  }

  @Test
  void resourceProfileUsesTheSameCompilerAsBuiltinAndDoesNotNeedUnusedVanillaInput()
      throws Exception {
    var memory = inputs();
    var mappings = new com.google.gson.JsonObject();
    for (var barrel : TARGETS.barrels()) {
      memory.files.remove(barrel.vanilla());
      mappings.addProperty(barrel.target().toString(), "example:models/chosen.json");
    }
    memory.profile("example:all", "{\"format\":1,\"models\":" + mappings + "}");
    memory.put(
        ResourceId.parse("example:models/chosen.json"), bytes(CUBE), ResourceAccess.Origin.OTHER);
    var result = compile(memory, settings("example:all", "lootr_styled:vanilla"));
    assertEquals(4, result.resources().ids().size());
    assertTrue(
        result.status().stream().noneMatch(s -> s.contains("refused") || s.contains("fallback")));
    for (var barrel : TARGETS.barrels())
      try (var stream = result.resources().open(barrel.target()).orElseThrow()) {
        assertEquals(CUBE, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
  }

  @Test
  void missingSelectionRetainsRequestedIdentityAndDoesNotFallBackToStyledDefault() {
    var memory = inputs();
    memory.put(
        BarrelCompiler.DEFAULT_MODEL, bytes("{\"elements\":[]}"), ResourceAccess.Origin.OTHER);
    var request = settings("missing:kept", "lootr_styled:vanilla");
    var result = compile(memory, request);
    assertEquals("missing:kept", request.models().toString());
    assertTrue(
        result.status().stream()
            .anyMatch(s -> s.contains("requested missing:kept, effective lootr_styled:vanilla")));
    assertTrue(result.status().stream().anyMatch(s -> s.contains("inherited geometry/UV")));
  }

  @Test
  void explicitResourceBeatsBuiltinButSelectedCustomProfileCanOverrideIt() {
    var memory = inputs();
    var target = TARGETS.barrels().getFirst().target();
    memory.put(target, bytes("{}"), ResourceAccess.Origin.OTHER);
    assertFalse(compile(memory, ClientSettings.DEFAULTS).resources().ids().contains(target));
    memory.profile(
        "example:chosen",
        "{\"format\":1,\"models\":{\"" + target + "\":\"example:models/chosen.json\"}}");
    memory.put(
        ResourceId.parse("example:models/chosen.json"), bytes(CUBE), ResourceAccess.Origin.OTHER);
    assertTrue(
        compile(memory, settings("example:chosen", "lootr_styled:vanilla"))
            .resources()
            .ids()
            .contains(target));
  }

  @Test
  void badProfileCannotImpersonateBuiltinOrEraseValidProfiles() {
    var memory = inputs();
    memory.profile("lootr_styled:default", "{\"format\":1,\"title\":\"Impersonator\"}");
    memory.profile("example:bad", "{\"format\":1,\"format\":1}");
    memory.profile("example:good", "{\"format\":1,\"title\":\"Good\"}");
    var result = compile(memory, ClientSettings.DEFAULTS);
    assertEquals(4, result.resources().ids().size());
    assertTrue(result.choices().stream().anyMatch(c -> c.id().toString().equals("example:good")));
    assertTrue(result.choices().stream().noneMatch(c -> c.title().equals("Impersonator")));
    assertEquals(
        2, result.status().stream().filter(s -> s.contains("invalid pack profile")).count());
  }

  @Test
  void artworkChoiceDoesNotActivateItsGeometry() throws Exception {
    var memory = inputs();
    byte[] image = png();
    memory.put(ResourceId.parse("example:textures/chest.png"), image, ResourceAccess.Origin.OTHER);
    memory.profile(
        "example:art",
        "{\"format\":1,\"models\":{\"other:models/unsupported.json\":\"missing:models/no.json\"},\"artwork\":{\"lootr:textures/chest.png\":\"example:textures/chest.png\"}}");
    var result = compile(memory, settings("lootr_styled:vanilla", "example:art"));
    assertEquals(5, result.resources().ids().size());
    try (var stream =
        result.resources().open(ResourceId.parse("lootr:textures/chest.png")).orElseThrow()) {
      assertArrayEquals(image, stream.readAllBytes());
    }
    assertTrue(result.status().stream().noneMatch(s -> s.contains("Model profile example:art")));
  }

  @Test
  void artworkFailurePublishesNoneOfThatProfilesPartialImageSet() throws Exception {
    var memory = inputs();
    memory.put(ResourceId.parse("example:textures/valid.png"), png(), ResourceAccess.Origin.OTHER);
    memory.put(
        ResourceId.parse("example:textures/bad.png"), bytes("bad"), ResourceAccess.Origin.OTHER);
    memory.profile(
        "example:art",
        "{\"format\":1,\"artwork\":{\"lootr:textures/chest.png\":\"example:textures/valid.png\",\"lootr:textures/chest_opened.png\":\"example:textures/bad.png\"}}");
    var result = compile(memory, settings("lootr_styled:default", "example:art"));
    assertEquals(4, result.resources().ids().size());
    assertTrue(
        result.status().stream()
            .anyMatch(
                s -> s.contains("Artwork: effective lootr_styled:vanilla after profile refusal")));
  }

  @Test
  void invalidModelProfileFallsBackAsAWholeWithoutDiscardingArtwork() throws Exception {
    var memory = inputs();
    memory.profile(
        "example:bad",
        "{\"format\":1,\"models\":{\"lootr:models/block/unknown.json\":\"example:models/unused.json\"}}");
    var result = compile(memory, settings("example:bad", "lootr_styled:vanilla"));
    assertEquals(4, result.resources().ids().size());
    assertTrue(
        result.status().stream()
            .anyMatch(
                s -> s.contains("Models: effective lootr_styled:vanilla after profile refusal")));
  }

  @Test
  void textureDecoderChecksBoundsAndFrameReferences() throws Exception {
    var images = new TextureImages();
    assertEquals(new TextureImages.Size(2, 2), images.validate(png()));
    assertThrows(IllegalArgumentException.class, () -> images.validate(bytes("not png")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TextureImages.validateMetadata(
                bytes("{\"animation\":{\"frames\":[1]}}"), new TextureImages.Size(2, 2)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TextureImages.validateMetadata(
                bytes("{\"animation\":{\"frametime\":0}}"), new TextureImages.Size(2, 2)));
    TextureImages.validateMetadata(
        bytes("{\"animation\":{\"frames\":[0]}}"), new TextureImages.Size(2, 2));
  }

  @Test
  void sharedInputBudgetCannotBeResetByOpeningAnotherProfileScope() {
    var budget = new ResourceInputs.Budget();
    for (int i = 0; i < ResourceInputs.MAX_FILES; i++) budget.lookup();
    assertThrows(
        ResourceInputs.LimitExceeded.class,
        () -> new ResourceInputs(inputs(), budget).find(BarrelCompiler.DEFAULT_MODEL));
    var bytes = new ResourceInputs.Budget();
    for (int i = 0; i < 8; i++) bytes.bytes(ResourceInputs.MAX_FILE_BYTES);
    assertThrows(ResourceInputs.LimitExceeded.class, () -> bytes.bytes(1));
  }

  private static final class Memory implements ResourceAccess {
    private final Map<ResourceId, Source> files = new HashMap<>();

    void put(ResourceId id, byte[] bytes, Origin origin) {
      files.put(id, new Source(origin, "fixture", () -> new ByteArrayInputStream(bytes)));
    }

    void profile(String id, String text) {
      var key = ResourceId.parse(id);
      put(
          new ResourceId(key.namespace(), ProfileCatalogueDirectory() + "/" + key.path() + ".json"),
          bytes(text),
          Origin.OTHER);
    }

    private static String ProfileCatalogueDirectory() {
      return design.inherent.lootr_styled.profile.ProfileCatalogue.DIRECTORY;
    }

    @Override
    public Optional<Source> find(ResourceId id) {
      return Optional.ofNullable(files.get(id));
    }

    @Override
    public Map<ResourceId, Source> list(String directory) {
      var result = new HashMap<ResourceId, Source>();
      files.forEach(
          (id, source) -> {
            if (id.path().startsWith(directory + "/")) result.put(id, source);
          });
      return result;
    }
  }
}
