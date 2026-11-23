package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.resources.BarrelCompiler;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.JsonDocuments;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native resource lookup and parser tests. No renderer/client/bootstrap claim. */
class NativeResourceGenerationTest {
  @TempDir Path config;

  private static final ContainerTargets TARGETS = ContainerTargets.LEGACY;

  private List<PackResources> inputs() throws Exception {
    var nativePack =
        new TestPack("mod/lootr", Optional.of(new KnownPack("neoforge", "mod/lootr", "test")));
    for (var barrel : TARGETS.barrels()) nativePack.put(barrel.target().toString(), "{}");
    var vanilla = new TestPack("vanilla", Optional.of(new KnownPack("minecraft", "core", "test")));
    vanilla.put("minecraft:models/block/block.json", "{}");
    for (var barrel : TARGETS.barrels()) {
      vanilla.put(
          barrel.vanilla().toString(),
          "{\"parent\":\"minecraft:block/block\",\"elements\":[{\"from\":[1,2,3],\"to\":[14,15,16],\"faces\":{\"up\":{\"uv\":[2,3,12,13],\"texture\":\"#top\"}}}],\"textures\":{\"top\":\"minecraft:block/barrel_top\"}}");
    }
    var own = new TestPack("mod/lootr_styled", Optional.empty());
    for (var barrel : TARGETS.barrels()) {
      for (boolean packed : List.of(false, true)) {
        var source = BarrelCompiler.defaultModel(barrel, packed);
        try (var stream =
            getClass().getResourceAsStream("/assets/" + source.namespace() + "/" + source.path())) {
          assertNotNull(stream);
          own.entries.put(ResourceLocation.parse(source.toString()), stream.readAllBytes());
        }
      }
      String state = barrel.material().equals("opened_barrel") ? "looted" : "unlooted";
      String path = "textures/block/default/barrel/" + state + ".png";
      try (var stream = getClass().getResourceAsStream("/assets/lootr_styled/" + path)) {
        assertNotNull(stream);
        own.entries.put(ResourceLocation.parse("lootr_styled:" + path), stream.readAllBytes());
      }
    }
    for (var entry : TARGETS.defaultArtwork().entrySet()) {
      var source = entry.getValue();
      try (var stream =
          getClass().getResourceAsStream("/assets/" + source.namespace() + "/" + source.path())) {
        assertNotNull(stream);
        byte[] image = stream.readAllBytes();
        own.entries.put(ResourceLocation.parse(source.toString()), image);
        nativePack.entries.put(ResourceLocation.parse(entry.getKey().toString()), image);
      }
    }
    return List.of(vanilla, nativePack, own);
  }

  private MultiPackResourceManager manager(List<PackResources> inputs) {
    StyleTestSources.initialize(config);
    ClientRuntime.initialize(config);
    return new MultiPackResourceManager(PackType.CLIENT_RESOURCES, GeneratedPack.append(inputs));
  }

  @org.junit.jupiter.api.AfterEach
  void closeResourceWorkers() {
    ClientRuntime.close();
  }

  @Test
  void isolatedPreparationBorrowsOnlyLowerSourcesAndRetiresWithoutClosingThem() throws Exception {
    var lower = inputs();
    try (var manager = manager(lower)) {
      var target = TARGETS.barrels().getFirst().target();
      var loaded = manager.getResource(ResourceLocation.parse(target.toString())).orElseThrow();
      assertEquals("lootr_styled/generated", loaded.sourcePackId());
      byte[] saved = Files.readAllBytes(config.resolve("lootr_styled/settings.json"));
      var generated =
          manager
              .listPacks()
              .filter(GeneratedPack.class::isInstance)
              .map(GeneratedPack.class::cast)
              .findFirst()
              .orElseThrow();
      var sources = generated.lowerSources();
      var found = sources.find(target).orElseThrow();
      var listed = sources.list("models/block").get(target);
      assertNotNull(listed);
      assertEquals(
          design.inherent.lootr_styled.resources.ResourceAccess.Origin.LOOTR, found.origin());
      try (var stream = found.opener().open()) {
        assertEquals("{}", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
      generated.close();
      assertThrows(java.util.concurrent.CancellationException.class, generated::lowerSources);
      assertThrows(java.util.concurrent.CancellationException.class, () -> sources.find(target));
      assertThrows(
          java.util.concurrent.CancellationException.class, () -> sources.list("models/block"));
      assertThrows(java.util.concurrent.CancellationException.class, () -> found.opener().open());
      assertThrows(java.util.concurrent.CancellationException.class, () -> listed.opener().open());
      assertArrayEquals(saved, Files.readAllBytes(config.resolve("lootr_styled/settings.json")));
      for (var pack : lower) assertEquals(0, ((TestPack) pack).closes);
    }
    for (var pack : lower) assertEquals(1, ((TestPack) pack).closes);
  }

  @Test
  void defaultArtworkUsesPackagedImagesAndVanillaSelectionRetainsNativeSources() throws Exception {
    try (var manager = manager(inputs())) {
      for (var entry : TARGETS.defaultArtwork().entrySet()) {
        var resource =
            manager.getResource(ResourceLocation.parse(entry.getKey().toString())).orElseThrow();
        assertEquals("lootr_styled/generated", resource.sourcePackId());
        var source = entry.getValue();
        try (var actual = resource.open();
            var expected =
                getClass()
                    .getResourceAsStream("/assets/" + source.namespace() + "/" + source.path())) {
          assertNotNull(expected);
          assertArrayEquals(expected.readAllBytes(), actual.readAllBytes());
        }
      }
    }
    Files.writeString(
        config.resolve("lootr_styled/settings.json"),
        ClientSettings.STARTER.replace(
            "\"artwork\": \"lootr_styled:default\"", "\"artwork\": \"lootr_styled:vanilla\""));
    try (var manager = manager(inputs())) {
      for (var target : TARGETS.artwork()) {
        assertEquals(
            "mod/lootr",
            manager
                .getResource(ResourceLocation.parse(target.toString()))
                .orElseThrow()
                .sourcePackId());
      }
    }
  }

  @Test
  void artworkExportKeepsOnlyMetadataAtOrAboveItsImageOwner() {
    var base = new TestPack("base", Optional.empty());
    var replacement = new TestPack("replacement", Optional.empty());
    var metadata = new TestPack("metadata", Optional.empty());
    String image = "lootr:textures/example.png", companion = image + ".mcmeta";
    base.put(image, "original");
    base.put(companion, "{}");
    replacement.put(image, "replacement");
    metadata.put(companion, "{\"animation\":{\"frametime\":2}}");
    try (var manager =
        new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(base, replacement))) {
      assertTrue(
          new NativeSources(manager)
              .find(design.inherent.lootr_styled.ResourceId.parse(companion))
              .isPresent());
      assertTrue(
          new NativeSources(manager, true)
              .find(design.inherent.lootr_styled.ResourceId.parse(companion))
              .isEmpty());
    }
    try (var manager =
        new MultiPackResourceManager(
            PackType.CLIENT_RESOURCES, List.of(base, replacement, metadata))) {
      assertEquals(
          "metadata",
          new NativeSources(manager, true)
              .find(design.inherent.lootr_styled.ResourceId.parse(companion))
              .orElseThrow()
              .description());
    }
  }

  @Test
  void defaultBarrelHasFourMaterialStatesAndDoesNotPublishVanillaModels() throws Exception {
    try (var manager = manager(inputs())) {
      for (var barrel : TARGETS.barrels()) {
        var resource =
            manager.getResource(ResourceLocation.parse(barrel.target().toString())).orElseThrow();
        assertEquals("lootr_styled/generated", resource.sourcePackId());
        try (var stream = resource.open()) {
          var model = JsonDocuments.object(stream.readAllBytes());
          assertEquals(barrel.open() ? 25 : 32, model.getAsJsonArray("elements").size());
          assertTrue(
              model
                  .get(design.inherent.lootr_styled.resources.BarrelCompiler.GENERATED_MARKER)
                  .getAsBoolean());
          assertTrue(model.getAsJsonObject("textures").has("surface"));
          assertEquals(
              "lootr:block/" + barrel.material() + "_side",
              model.getAsJsonObject("textures").get("particle").getAsString());
        }
      }
      var original =
          manager
              .getResource(ResourceLocation.parse("minecraft:models/block/barrel.json"))
              .orElseThrow();
      assertEquals("vanilla", original.sourcePackId());
      assertTrue(Files.isRegularFile(config.resolve("lootr_styled/settings.json")));
    }
  }

  @Test
  void inheritancePreservesGeometryAndUvsWhileKeepingLootrMaterials() throws Exception {
    Files.createDirectories(config.resolve("lootr_styled"));
    Files.writeString(
        config.resolve("lootr_styled/settings.json"),
        ClientSettings.STARTER.replace("lootr_styled:default", "lootr_styled:vanilla"));
    try (var manager = manager(inputs())) {
      for (var barrel : TARGETS.barrels()) {
        try (var stream =
            manager
                .getResource(ResourceLocation.parse(barrel.target().toString()))
                .orElseThrow()
                .open()) {
          var model = JsonDocuments.object(stream.readAllBytes());
          assertEquals(1, model.getAsJsonArray("elements").size());
          assertEquals(
              "[1,2,3]",
              model.getAsJsonArray("elements").get(0).getAsJsonObject().get("from").toString());
          assertEquals(
              "[2,3,12,13]",
              model
                  .getAsJsonArray("elements")
                  .get(0)
                  .getAsJsonObject()
                  .getAsJsonObject("faces")
                  .getAsJsonObject("up")
                  .get("uv")
                  .toString());
        }
      }
    }
  }

  @Test
  void explicitLootrResourceWinsEvenWhenItsBytesEqualTheNativeResource() throws Exception {
    var lower = new java.util.ArrayList<>(inputs());
    var explicit = new TestPack("author-selected", Optional.empty());
    String target = TARGETS.barrels().getFirst().target().toString();
    explicit.put(target, "{}");
    lower.add(explicit);
    try (var manager = manager(lower)) {
      assertEquals(
          "author-selected",
          manager.getResource(ResourceLocation.parse(target)).orElseThrow().sourcePackId());
    }
  }

  @Test
  void closingOwnedLayerDoesNotCloseBorrowedPacksAndRetiresItsSuppliers() throws Exception {
    var lower = inputs();
    StyleTestSources.initialize(config);
    ClientRuntime.initialize(config);
    var full = GeneratedPack.append(lower);
    var generated = full.getLast();
    var supplier =
        generated.getResource(
            PackType.CLIENT_RESOURCES,
            ResourceLocation.parse(TARGETS.barrels().getFirst().target().toString()));
    assertNotNull(supplier);
    generated.close();
    assertThrows(IOException.class, supplier::get);
    assertNull(
        generated.getResource(
            PackType.CLIENT_RESOURCES,
            ResourceLocation.parse(TARGETS.barrels().getFirst().target().toString())));
    for (var pack : lower) assertEquals(0, ((TestPack) pack).closes);
  }

  @Test
  void closingDuringPreparationCannotOpenAnotherBorrowedSource() throws Exception {
    var lower = inputs();
    StyleTestSources.initialize(config);
    ClientRuntime.initialize(config);
    var entered = new java.util.concurrent.CountDownLatch(1);
    var resume = new java.util.concurrent.CountDownLatch(1);
    var once = new java.util.concurrent.atomic.AtomicBoolean();
    var retired = new java.util.concurrent.atomic.AtomicBoolean();
    var lateOpens = new java.util.concurrent.atomic.AtomicInteger();
    for (var pack : lower) {
      var source = (TestPack) pack;
      source.beforeLookup =
          () -> {
            if (!once.compareAndSet(false, true)) return;
            entered.countDown();
            try {
              if (!resume.await(5, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("Controlled source release timed out");
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(interrupted);
            }
          };
      source.onOpen =
          () -> {
            if (retired.get()) lateOpens.incrementAndGet();
          };
    }
    var workers = ClientRuntime.workers();
    var generated = GeneratedPack.append(lower).getLast();
    try {
      try {
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        retired.set(true);
        generated.close();
      } finally {
        resume.countDown();
      }
      ClientRuntime.close();
      assertTrue(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(0, lateOpens.get(), "A retired generation must not reopen borrowed resources");
      for (var pack : lower) assertEquals(0, ((TestPack) pack).closes);
    } finally {
      resume.countDown();
      generated.close();
      ClientRuntime.close();
    }
  }

  @Test
  void missingSettingsNeverCreateAnImplicitEnabledGeneration() throws Exception {
    ClientRuntime.initialize(config);
    var id = ResourceLocation.parse(TARGETS.barrels().getFirst().target().toString());
    try (var before = GeneratedPack.append(inputs()).getLast()) {
      assertNull(before.getResource(PackType.CLIENT_RESOURCES, id));
      assertFalse(Files.exists(config.resolve("lootr_styled/settings.json")));
    }
    StyleTestSources.initialize(config);
    try (var after = GeneratedPack.append(inputs()).getLast()) {
      assertNotNull(after.getResource(PackType.CLIENT_RESOURCES, id));
    }
  }

  private static final class TestPack implements PackResources {
    private final PackLocationInfo location;
    private final Map<ResourceLocation, byte[]> entries = new HashMap<>();
    private int closes;
    private Runnable beforeLookup = () -> {};
    private Runnable onOpen = () -> {};

    TestPack(String id, Optional<KnownPack> known) {
      location = new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, known);
    }

    void put(String id, String value) {
      entries.put(ResourceLocation.parse(id), value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PackLocationInfo location() {
      return location;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
      return null;
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> section) {
      return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
      byte[] value = type == PackType.CLIENT_RESOURCES ? entries.get(id) : null;
      if (value == null) return null;
      beforeLookup.run();
      return () -> {
        onOpen.run();
        return new ByteArrayInputStream(value);
      };
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
      return type == PackType.CLIENT_RESOURCES
          ? entries.keySet().stream()
              .map(ResourceLocation::getNamespace)
              .collect(java.util.stream.Collectors.toSet())
          : Set.of();
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
      if (type != PackType.CLIENT_RESOURCES) return;
      entries.forEach(
          (id, bytes) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(path + "/"))
              output.accept(id, () -> new ByteArrayInputStream(bytes));
          });
    }

    @Override
    public void close() {
      closes++;
    }
  }
}
