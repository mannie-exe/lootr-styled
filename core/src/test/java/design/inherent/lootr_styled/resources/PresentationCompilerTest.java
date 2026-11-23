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
    for (var barrel : TARGETS.barrels()) {
      memory.put(
          BarrelCompiler.defaultModel(barrel, false), bytes(CUBE), ResourceAccess.Origin.OTHER);
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

  private static Memory artworkInputs(ContainerTargets targets) throws Exception {
    var memory = new Memory();
    for (var entry : targets.defaultArtwork().entrySet()) {
      var source = entry.getValue();
      try (var stream =
          PresentationCompilerTest.class.getResourceAsStream(
              "/assets/" + source.namespace() + "/" + source.path())) {
        assertNotNull(stream, source.toString());
        memory.put(source, stream.readAllBytes(), ResourceAccess.Origin.OTHER);
      }
      memory.put(entry.getKey(), png(), ResourceAccess.Origin.LOOTR);
    }
    return memory;
  }

  private static Memory authoredInputs() throws Exception {
    var memory = artworkInputs(TARGETS);
    memory.put(
        ResourceId.parse("minecraft:models/block/block.json"),
        bytes("{}"),
        ResourceAccess.Origin.MINECRAFT);
    for (var barrel : TARGETS.barrels()) {
      memory.put(barrel.target(), bytes("{}"), ResourceAccess.Origin.LOOTR);
      memory.put(barrel.vanilla(), bytes(CUBE), ResourceAccess.Origin.MINECRAFT);
      for (boolean packed : java.util.List.of(false, true)) {
        var id = BarrelCompiler.defaultModel(barrel, packed);
        try (var stream =
            PresentationCompilerTest.class.getResourceAsStream(
                "/assets/" + id.namespace() + "/" + id.path())) {
          assertNotNull(stream);
          memory.put(id, stream.readAllBytes(), ResourceAccess.Origin.OTHER);
        }
      }
      var id = BarrelCompiler.defaultSurface(barrel);
      try (var stream =
          PresentationCompilerTest.class.getResourceAsStream(
              "/assets/" + id.namespace() + "/" + id.path())) {
        assertNotNull(stream);
        memory.put(id, stream.readAllBytes(), ResourceAccess.Origin.OTHER);
      }
    }
    return memory;
  }

  private static com.google.gson.JsonObject model(
      PresentationCompiler.Result result, ContainerTargets.Barrel barrel) throws Exception {
    try (var stream = result.resources().open(barrel.target()).orElseThrow()) {
      return JsonDocuments.object(stream.readAllBytes());
    }
  }

  private static boolean occupied(com.google.gson.JsonObject model, double x, double y, double z) {
    double[] point = {x, y, z};
    for (var value : model.getAsJsonArray("elements")) {
      var element = value.getAsJsonObject();
      boolean inside = true;
      for (int axis = 0; axis < 3; axis++) {
        inside &=
            point[axis] >= element.getAsJsonArray("from").get(axis).getAsDouble()
                && point[axis] < element.getAsJsonArray("to").get(axis).getAsDouble();
      }
      if (inside) return true;
    }
    return false;
  }

  @Test
  void completeDefaultModelsHaveRealCavitiesAndIndependentArtworkBindings() throws Exception {
    var memory = authoredInputs();
    var painted = compile(memory, settings("lootr_styled:default", "lootr_styled:default"));
    var inherited = compile(memory, settings("lootr_styled:default", "lootr_styled:vanilla"));
    assertTrue(
        painted.status().stream().noneMatch(s -> s.contains("refused") || s.contains("fallback")));
    for (var barrel : TARGETS.barrels()) {
      var packed = model(painted, barrel);
      var nativeUv = model(inherited, barrel);
      assertEquals(barrel.open() ? 25 : 32, packed.getAsJsonArray("elements").size());
      assertEquals(
          packed.getAsJsonArray("elements").size(), nativeUv.getAsJsonArray("elements").size());
      assertTrue(packed.getAsJsonObject("textures").has("surface"));
      assertFalse(nativeUv.getAsJsonObject("textures").has("surface"));
      assertEquals(!barrel.open(), occupied(packed, 8, 8, 8));
      assertEquals(!barrel.open(), occupied(nativeUv, 8, 8, 8));
      assertTrue(occupied(packed, 8, .5, 8));
      assertEquals(
          "lootr:block/" + barrel.material() + "_bottom",
          nativeUv.getAsJsonObject("textures").get("bottom").getAsString());
    }
  }

  @Test
  void explicitAndPartialArtworkOverridesCannotKeepThePrepaintedDefaultSkin() throws Exception {
    var memory = authoredInputs();
    var target = ResourceId.parse("lootr:textures/block/barrel_side.png");
    byte[] equalDefault;
    try (var stream =
        memory.find(TARGETS.defaultArtwork().get(target)).orElseThrow().opener().open()) {
      equalDefault = stream.readAllBytes();
    }
    memory.put(target, equalDefault, ResourceAccess.Origin.OTHER);
    var explicit = compile(memory, settings("lootr_styled:default", "lootr_styled:default"));
    assertEquals(
        ClientSettings.DEFAULT,
        explicit.selections().orElseThrow().artwork().effective().orElseThrow());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.SELECTED,
        explicit.selections().orElseThrow().artwork().state());
    assertFalse(explicit.resources().ids().contains(target));
    for (var barrel : TARGETS.barrels()) {
      assertEquals(
          barrel.material().equals("opened_barrel"),
          model(explicit, barrel).getAsJsonObject("textures").has("surface"));
    }
    memory.profile(
        "example:partial",
        "{\"format\":1,\"artwork\":{\"lootr:textures/block/barrel_side.png\":\"example:textures/side.png\"}}");
    memory.put(ResourceId.parse("example:textures/side.png"), png(), ResourceAccess.Origin.OTHER);
    var partial = compile(memory, settings("lootr_styled:default", "example:partial"));
    for (var barrel : TARGETS.barrels())
      assertFalse(model(partial, barrel).getAsJsonObject("textures").has("surface"));
  }

  @Test
  void refusedArtworkAndUnavailablePackedSurfaceKeepProjectedGeometry() throws Exception {
    var memory = authoredInputs();
    memory.put(
        ResourceId.parse("lootr_styled:textures/default/chest.png"),
        bytes("corrupt"),
        ResourceAccess.Origin.OTHER);
    var refused = compile(memory, settings("lootr_styled:default", "lootr_styled:default"));
    for (var barrel : TARGETS.barrels())
      assertFalse(model(refused, barrel).getAsJsonObject("textures").has("surface"));
    assertTrue(refused.resources().ids().stream().noneMatch(id -> id.path().endsWith(".png")));
    memory = authoredInputs();
    var open =
        TARGETS.barrels().stream().filter(ContainerTargets.Barrel::open).findFirst().orElseThrow();
    memory.put(BarrelCompiler.defaultSurface(open), bytes("corrupt"), ResourceAccess.Origin.OTHER);
    var absent = compile(memory, settings("lootr_styled:default", "lootr_styled:default"));
    assertFalse(model(absent, open).getAsJsonObject("textures").has("surface"));
    assertFalse(occupied(model(absent, open), 8, 8, 8));
    assertTrue(absent.resources().ids().contains(ResourceId.parse("lootr:textures/chest.png")));
  }

  @Test
  void defaultArtworkCoversBothNativeTargetsWithoutEnablingModelChanges() throws Exception {
    assertEquals(ClientSettings.DEFAULT, ClientSettings.DEFAULTS.artwork());
    assertEquals(ClientSettings.DEFAULTS, ClientSettings.parse(bytes(ClientSettings.STARTER)));
    for (var targets : ContainerTargets.values()) {
      var memory = artworkInputs(targets);
      var result =
          PresentationCompiler.compile(
              targets,
              settings("lootr_styled:vanilla", "lootr_styled:default"),
              memory,
              null,
              bytes -> BlockModels.validate(JsonDocuments.object(bytes)));
      assertEquals(targets.artwork(), result.resources().ids());
      assertEquals(12, result.resources().ids().size());
      assertTrue(result.status().stream().noneMatch(s -> s.contains("refused")));
      for (var entry : targets.defaultArtwork().entrySet()) {
        try (var actual = result.resources().open(entry.getKey()).orElseThrow();
            var expected = memory.find(entry.getValue()).orElseThrow().opener().open()) {
          assertArrayEquals(expected.readAllBytes(), actual.readAllBytes());
        }
      }
      var inherited =
          PresentationCompiler.compile(
              targets,
              settings("lootr_styled:vanilla", "lootr_styled:vanilla"),
              memory,
              null,
              bytes -> BlockModels.validate(JsonDocuments.object(bytes)));
      assertTrue(inherited.resources().ids().isEmpty());
    }
  }

  @Test
  void defaultArtworkKeepsExplicitPackImagesButCustomSelectionCanReplaceThem() throws Exception {
    var memory = artworkInputs(TARGETS);
    var target = ResourceId.parse("lootr:textures/chest.png");
    byte[] explicit = png();
    memory.put(target, explicit, ResourceAccess.Origin.OTHER);
    var result = compile(memory, settings("lootr_styled:vanilla", "lootr_styled:default"));
    assertFalse(result.resources().ids().contains(target));
    assertEquals(11, result.resources().ids().size());
    memory.profile(
        "example:chosen",
        "{\"format\":1,\"artwork\":{\"lootr:textures/chest.png\":\"lootr_styled:textures/default/chest.png\"}}");
    assertTrue(
        compile(memory, settings("lootr_styled:vanilla", "example:chosen"))
            .resources()
            .ids()
            .contains(target));
    var disabled =
        new ClientSettings(
            false, ClientSettings.DEFAULT, ClientSettings.DEFAULT, ClientSettings.Fresh.OFF);
    assertTrue(compile(memory, disabled).resources().ids().isEmpty());
  }

  @Test
  void corruptDefaultImageDoesNotPublishAPartialArtworkSet() throws Exception {
    var memory = artworkInputs(TARGETS);
    memory.put(
        ResourceId.parse("lootr_styled:textures/default/block/barrel_top.png"),
        bytes("invalid"),
        ResourceAccess.Origin.OTHER);
    var result = compile(memory, settings("lootr_styled:vanilla", "lootr_styled:default"));
    assertTrue(result.resources().ids().isEmpty());
    assertTrue(
        result.status().stream()
            .anyMatch(s -> s.contains("Artwork profile lootr_styled:default refused")));
  }

  @Test
  void disabledStylingStillListsProfileTitlesWithoutCompilingTheirAssets() {
    var memory = new Memory();
    memory.put(
        ResourceId.parse("example:lootr_styled/profiles/test.json"),
        bytes(
            "{\"format\":1,\"title\":\"Test\",\"models\":{\"lootr:models/block/lootr_barrel_unopened.json\":\"missing:models/unused.json\"}}"),
        ResourceAccess.Origin.OTHER);
    var settings =
        new ClientSettings(
            false, ClientSettings.DEFAULT, ClientSettings.VANILLA, ClientSettings.Fresh.ON);
    var result = compile(memory, settings);
    var resolutions = result.selections().orElseThrow();
    assertEquals(settings.models(), resolutions.models().requested());
    assertEquals(settings.artwork(), resolutions.artwork().requested());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.DISABLED, resolutions.models().state());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.DISABLED, resolutions.artwork().state());
    assertTrue(resolutions.models().effective().isEmpty());
    assertTrue(resolutions.artwork().effective().isEmpty());
    assertTrue(resolutions.models().source().isEmpty());
    assertTrue(result.resources().ids().isEmpty());
    assertTrue(
        result.choices().stream()
            .anyMatch(
                c ->
                    c.id().equals(ClientSettings.VANILLA)
                        && c.title().equals("Vanilla (Inherited)")));
    assertTrue(
        result.choices().stream()
            .anyMatch(
                c -> c.id().equals(ResourceId.parse("example:test")) && c.title().equals("Test")));
    assertEquals(java.util.List.of("Disabled"), result.status());
    var custom =
        result.choices().stream()
            .filter(c -> c.id().equals(ResourceId.parse("example:test")))
            .findFirst()
            .orElseThrow();
    assertEquals(
        new PresentationCompiler.ContributionCounts(1, 0), custom.declarations().orElseThrow());
    var inherited =
        result.choices().stream()
            .filter(c -> c.id().equals(ClientSettings.VANILLA))
            .findFirst()
            .orElseThrow();
    assertEquals(
        new PresentationCompiler.ContributionCounts(0, 0), inherited.declarations().orElseThrow());
  }

  @Test
  void actualLocalModelAndPngCompileWithoutPublishingAVanillaOverride(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
    var memory = inputs();
    var directory = root.resolve("custom");
    java.nio.file.Files.createDirectories(directory.resolve("assets/example/models"));
    java.nio.file.Files.createDirectories(directory.resolve("assets/example/textures/block"));
    String target = TARGETS.barrels().getFirst().target().toString();
    java.nio.file.Files.writeString(
        directory.resolve("profile.json"),
        "{\"format\":1,\"models\":{\"" + target + "\":\"example:models/local.json\"}}");
    java.nio.file.Files.writeString(
        directory.resolve("assets/example/models/local.json"),
        "{\"textures\":{\"side\":\"example:block/local\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{\"texture\":\"#side\",\"uv\":[1,2,3,4]}}}]}");
    byte[] image = png();
    java.nio.file.Files.write(directory.resolve("assets/example/textures/block/local.png"), image);
    try (var local = design.inherent.lootr_styled.profile.LocalFiles.open(root)) {
      var result =
          PresentationCompiler.compile(
              TARGETS,
              settings("lootr_styled_local:custom", "lootr_styled:vanilla"),
              memory,
              local,
              bytes -> BlockModels.validate(JsonDocuments.object(bytes)));
      local.validateCurrent();
      assertTrue(
          result.resources().ids().stream().noneMatch(id -> id.namespace().equals("minecraft")));
      var texture =
          result.resources().ids().stream()
              .filter(id -> id.path().endsWith(".png"))
              .findFirst()
              .orElseThrow();
      try (var input = result.resources().open(texture).orElseThrow()) {
        assertArrayEquals(image, input.readAllBytes());
      }
      try (var input = result.resources().open(ResourceId.parse(target)).orElseThrow()) {
        var model = JsonDocuments.object(input.readAllBytes());
        assertTrue(
            model
                .getAsJsonObject("textures")
                .get("side")
                .getAsString()
                .startsWith("lootr_styled:block/generated/"));
        assertEquals(
            "[1,2,3,4]",
            model
                .getAsJsonArray("elements")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("faces")
                .getAsJsonObject("north")
                .get("uv")
                .toString());
      }
    }
  }

  @Test
  void recognizedNativeOpenWrapperUsesOpenLeavesButForeignWrappersRemainOwned() throws Exception {
    var memory = inputs();
    var wrapper = ResourceId.parse("lootr:models/block/lootr_barrel_open.json");
    String declaration =
        "{\"loader\":\"lootr:barrel\",\"unopened\":{\"parent\":\"lootr:block/lootr_barrel_unopened\"},\"opened\":{\"parent\":\"lootr:block/lootr_opened_barrel\"},\"old_opened\":{\"parent\":\"lootr:block/old_lootr_opened_barrel_open\"}}";
    memory.put(wrapper, bytes(declaration), ResourceAccess.Origin.LOOTR);
    var result = compile(memory, ClientSettings.DEFAULTS);
    try (var stream = result.resources().open(wrapper).orElseThrow()) {
      var parsed = JsonDocuments.object(stream.readAllBytes());
      assertEquals(
          "lootr:block/lootr_barrel_unopened_open",
          parsed.getAsJsonObject("unopened").get("parent").getAsString());
      assertEquals(
          "lootr:block/lootr_opened_barrel_open",
          parsed.getAsJsonObject("opened").get("parent").getAsString());
      assertEquals(
          "lootr:block/old_lootr_opened_barrel_open",
          parsed.getAsJsonObject("old_opened").get("parent").getAsString());
    }
    memory.put(wrapper, bytes(declaration), ResourceAccess.Origin.OTHER);
    assertFalse(compile(memory, ClientSettings.DEFAULTS).resources().ids().contains(wrapper));
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
    var expected = JsonDocuments.object(bytes(CUBE));
    expected.addProperty(BarrelCompiler.GENERATED_MARKER, true);
    for (var barrel : TARGETS.barrels())
      try (var stream = result.resources().open(barrel.target()).orElseThrow()) {
        assertEquals(expected, JsonDocuments.object(stream.readAllBytes()));
      }
  }

  @Test
  void olderProducerDiagnosticsCannotInventStructuredResolution() {
    var legacy =
        new PresentationCompiler.Result(
            FrozenResources.EMPTY,
            java.util.List.of("Models: effective lootr_styled:default"),
            java.util.List.of());
    assertTrue(legacy.selections().isEmpty());
  }

  @Test
  void missingArtworkAndDisabledSelectionsDoNotInventAnotherEffectiveProfile() {
    var memory = inputs();
    var request = settings("lootr_styled:default", "missing:artwork");
    var selected = compile(memory, request).selections().orElseThrow();
    assertEquals(ClientSettings.DEFAULT, selected.models().effective().orElseThrow());
    assertEquals(PresentationCompiler.ProfileResolution.State.SELECTED, selected.models().state());
    assertEquals(request.artwork(), selected.artwork().requested());
    assertEquals(ClientSettings.VANILLA, selected.artwork().effective().orElseThrow());
    assertEquals(PresentationCompiler.ProfileResolution.State.MISSING, selected.artwork().state());
    var disabled =
        compile(
                memory,
                new ClientSettings(
                    false,
                    ResourceId.parse("missing:models"),
                    request.artwork(),
                    ClientSettings.Fresh.AUTO))
            .selections()
            .orElseThrow();
    assertEquals(ResourceId.parse("missing:models"), disabled.models().requested());
    assertEquals(request.artwork(), disabled.artwork().requested());
    assertTrue(disabled.models().effective().isEmpty());
    assertTrue(disabled.artwork().effective().isEmpty());
    assertEquals(PresentationCompiler.ProfileResolution.State.DISABLED, disabled.artwork().state());
  }

  @Test
  void missingSelectionRetainsRequestedIdentityAndDoesNotFallBackToStyledDefault() {
    var memory = inputs();
    memory.put(
        BarrelCompiler.defaultModel(TARGETS.barrels().getFirst(), false),
        bytes("{\"elements\":[]}"),
        ResourceAccess.Origin.OTHER);
    var request = settings("missing:kept", "lootr_styled:vanilla");
    var result = compile(memory, request);
    assertEquals("missing:kept", request.models().toString());
    var resolutions = result.selections().orElseThrow();
    assertEquals(request.models(), resolutions.models().requested());
    assertEquals(ClientSettings.VANILLA, resolutions.models().effective().orElseThrow());
    assertEquals("Built-in", resolutions.models().source().orElseThrow());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.MISSING, resolutions.models().state());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.SELECTED, resolutions.artwork().state());
    assertTrue(resolutions.models().detail().isPresent());
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
    var resolutions = result.selections().orElseThrow();
    assertEquals(ResourceId.parse("example:art"), resolutions.artwork().requested());
    assertEquals(ClientSettings.VANILLA, resolutions.artwork().effective().orElseThrow());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.REFUSED, resolutions.artwork().state());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.SELECTED, resolutions.models().state());
    assertEquals(ClientSettings.DEFAULT, resolutions.models().effective().orElseThrow());
    assertTrue(resolutions.artwork().detail().isPresent());
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
    var resolutions = result.selections().orElseThrow();
    assertEquals(ResourceId.parse("example:bad"), resolutions.models().requested());
    assertEquals(ClientSettings.VANILLA, resolutions.models().effective().orElseThrow());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.REFUSED, resolutions.models().state());
    assertEquals(
        PresentationCompiler.ProfileResolution.State.SELECTED, resolutions.artwork().state());
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
        () ->
            new ResourceInputs(inputs(), budget)
                .find(BarrelCompiler.defaultModel(TARGETS.barrels().getFirst(), false)));
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
