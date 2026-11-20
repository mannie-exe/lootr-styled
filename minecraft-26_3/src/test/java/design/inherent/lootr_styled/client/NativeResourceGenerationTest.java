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
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native resource lookup and parser tests. No renderer/client/bootstrap claim. */
class NativeResourceGenerationTest {
  @TempDir Path config;

  private static final ContainerTargets TARGETS = ContainerTargets.MODERN;

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
    try (var stream =
        getClass().getResourceAsStream("/assets/lootr_styled/models/block/barrel_clasps.json")) {
      assertNotNull(stream);
      own.entries.put(
          Identifier.parse(BarrelCompiler.DEFAULT_MODEL.toString()), stream.readAllBytes());
    }
    return List.of(vanilla, nativePack, own);
  }

  private MultiPackResourceManager manager(List<PackResources> inputs) {
    ClientRuntime.initialize(config);
    return new MultiPackResourceManager(PackType.CLIENT_RESOURCES, GeneratedPack.append(inputs));
  }

  @Test
  void defaultBarrelHasFourMaterialStatesAndDoesNotPublishVanillaModels() throws Exception {
    try (var manager = manager(inputs())) {
      for (var barrel : TARGETS.barrels()) {
        var resource =
            manager.getResource(Identifier.parse(barrel.target().toString())).orElseThrow();
        assertEquals("lootr_styled/generated", resource.sourcePackId());
        try (var stream = resource.open()) {
          var model = JsonDocuments.object(stream.readAllBytes());
          assertEquals(169, model.getAsJsonArray("elements").size());
          assertEquals(
              "lootr:block/" + barrel.material() + "_" + (barrel.open() ? "top_open" : "top"),
              model.getAsJsonObject("textures").get("top").getAsString());
        }
      }
      var original =
          manager.getResource(Identifier.parse("minecraft:models/block/barrel.json")).orElseThrow();
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
                .getResource(Identifier.parse(barrel.target().toString()))
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
          manager.getResource(Identifier.parse(target)).orElseThrow().sourcePackId());
    }
  }

  @Test
  void closingOwnedLayerDoesNotCloseBorrowedPacksAndRetiresItsSuppliers() throws Exception {
    var lower = inputs();
    ClientRuntime.initialize(config);
    var full = GeneratedPack.append(lower);
    var generated = full.getLast();
    var supplier =
        generated.getResource(
            PackType.CLIENT_RESOURCES,
            Identifier.parse(TARGETS.barrels().getFirst().target().toString()));
    assertNotNull(supplier);
    generated.close();
    assertThrows(IOException.class, supplier::get);
    assertNull(
        generated.getResource(
            PackType.CLIENT_RESOURCES,
            Identifier.parse(TARGETS.barrels().getFirst().target().toString())));
    for (var pack : lower) assertEquals(0, ((TestPack) pack).closes);
  }

  private static final class TestPack implements PackResources {
    private final PackLocationInfo location;
    private final Map<Identifier, byte[]> entries = new HashMap<>();
    private int closes;

    TestPack(String id, Optional<KnownPack> known) {
      location = new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, known);
    }

    void put(String id, String value) {
      entries.put(Identifier.parse(id), value.getBytes(StandardCharsets.UTF_8));
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
    public <T> T getMetadataSection(MetadataSectionType<T> section) {
      return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
      byte[] value = type == PackType.CLIENT_RESOURCES ? entries.get(id) : null;
      return value == null ? null : () -> new ByteArrayInputStream(value);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
      return type == PackType.CLIENT_RESOURCES
          ? entries.keySet().stream()
              .map(Identifier::getNamespace)
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
