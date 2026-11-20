package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.ProfileCatalogue;
import design.inherent.lootr_styled.profile.StyleProfile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RigAdapterTest {
  private static final ResourceId SOURCE = ResourceId.parse("minecraft:emf/cem/chest.jem");
  private static final ResourceId TARGET = ContainerTargets.LEGACY.chests().getFirst().target();
  private static final String RIG =
      "{\"credit\":\"base.png label\",\"textureSize\":[64,64],\"models\":[{\"part\":\"root\",\"id\":\"root\",\"attach\":true,\"model\":\"motion.jpm\"},{\"part\":\"lid\",\"boxes\":[{\"coordinates\":[1,2,3,14,5,14],\"textureOffset\":[3,7]}]},{\"part\":\"base\",\"id\":\"base\"},{\"part\":\"knob\",\"id\":\"knob\"}]}";
  private static final String MOTION =
      "{\"credit\":\"base.png label\",\"animations\":[{\"base.tx\":\"1 + base.tx + database.tx\",\"knob.rx\":\"base.rx\"}]}";
  private static final EmfSupport PRESENT = EmfSupport.inspect(Optional.of("3.3.12-neoforge-1.21"));

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static Memory inputs() {
    var memory = new Memory();
    memory.put(SOURCE, RIG);
    memory.put(ResourceId.parse("minecraft:emf/cem/motion.jpm"), MOTION);
    return memory;
  }

  private static Map<ResourceId, byte[]> compile(Memory inputs) {
    return RigAdapter.compile(new ResourceInputs(inputs), SOURCE, TARGET, true);
  }

  private static ProfileCatalogue.Entry inherited(Memory source) {
    return new ProfileCatalogue.Entry(
        new StyleProfile(ClientSettings.VANILLA, "Inherited", Map.of(), Map.of()),
        source,
        "fixture");
  }

  @Test
  void adaptationPreservesShapeUvsCreditAndSourceBytes() {
    var inputs = inputs();
    var result = compile(inputs);
    assertEquals(2, result.size());
    var root = JsonDocuments.object(result.get(TARGET));
    assertEquals(
        List.of("root", "lid", "bottom", "lock"),
        root.getAsJsonArray("models").asList().stream()
            .map(x -> x.getAsJsonObject().get("part").getAsString())
            .toList());
    var box =
        root.getAsJsonArray("models")
            .get(1)
            .getAsJsonObject()
            .getAsJsonArray("boxes")
            .get(0)
            .getAsJsonObject();
    assertEquals("[1,2,3,14,5,14]", box.get("coordinates").toString());
    assertEquals("[3,7]", box.get("textureOffset").toString());
    assertEquals("base.png label", root.get("credit").getAsString());
    assertArrayEquals(bytes(RIG), inputs.files.get(SOURCE));
    String renamed =
        root.getAsJsonArray("models").get(0).getAsJsonObject().get("model").getAsString();
    assertTrue(renamed.startsWith("lootr_styled_"));
    var motion = JsonDocuments.object(result.get(ResourceId.parse("minecraft:emf/cem/" + renamed)));
    assertEquals("base.png label", motion.get("credit").getAsString());
    var animation = motion.getAsJsonArray("animations").get(0).getAsJsonObject();
    assertEquals("1 + bottom.tx + database.tx", animation.get("bottom.tx").getAsString());
    assertEquals("bottom.rx", animation.get("lock.rx").getAsString());
  }

  @Test
  void independentRootTargetsCannotAliasAnimationStateResources() {
    var inputs = inputs();
    var a = compile(inputs);
    var b =
        RigAdapter.compile(
            new ResourceInputs(inputs),
            SOURCE,
            ContainerTargets.LEGACY.chests().get(1).target(),
            true);
    assertTrue(java.util.Collections.disjoint(a.keySet(), b.keySet()));
  }

  @Test
  void absenceAndUnknownVersionAvoidRigLookup() {
    for (var capability : List.of(EmfSupport.ABSENT, EmfSupport.inspect(Optional.of("4.0.0")))) {
      var inputs = inputs();
      var status = new ArrayList<String>();
      var result =
          ChestPresentation.compile(
              ContainerTargets.LEGACY,
              ClientSettings.Fresh.ON,
              capability,
              inherited(inputs),
              new ResourceInputs(inputs),
              new ResourceInputs.Budget(),
              status);
      assertTrue(result.isEmpty());
      assertTrue(inputs.reads.isEmpty());
      assertTrue(status.stream().anyMatch(s -> s.contains(capability.reason())));
    }
    assertFalse(EmfSupport.inspect(Optional.of("3.3.11")).admitted());
    assertTrue(PRESENT.admitted());
  }

  @Test
  void offPreparesNativeDeclarationsWithoutReadingVanillaRigs() {
    var inputs = inputs();
    var result =
        ChestPresentation.compile(
            ContainerTargets.LEGACY,
            ClientSettings.Fresh.OFF,
            PRESENT,
            inherited(inputs),
            new ResourceInputs(inputs),
            new ResourceInputs.Budget(),
            new ArrayList<>());
    assertEquals(2, result.size());
    assertFalse(inputs.reads.contains(SOURCE));
    for (var value : result.values())
      assertTrue(JsonDocuments.object(value).getAsJsonArray("models").isEmpty());
  }

  @Test
  void offStillPreservesExplicitLootrResources() {
    var inputs = inputs();
    inputs.put(TARGET, "{\"explicit\":true}");
    var result =
        ChestPresentation.compile(
            ContainerTargets.LEGACY,
            ClientSettings.Fresh.OFF,
            PRESENT,
            inherited(inputs),
            new ResourceInputs(inputs),
            new ResourceInputs.Budget(),
            new ArrayList<>());
    assertFalse(result.containsKey(TARGET));
  }

  @Test
  void onMissingInputReportsNativeFallback() {
    var inputs = new Memory();
    var status = new ArrayList<String>();
    var result =
        ChestPresentation.compile(
            ContainerTargets.LEGACY,
            ClientSettings.Fresh.ON,
            PRESENT,
            inherited(inputs),
            new ResourceInputs(inputs),
            new ResourceInputs.Budget(),
            status);
    assertEquals(2, result.size());
    assertTrue(status.stream().anyMatch(s -> s.contains("No effective vanilla chest rig")));
  }

  @Test
  void explicitLootrRigDoesNotRequireUnusedVanillaRig() {
    var inputs = new Memory();
    var custom = ResourceId.parse("example:emf/cem/direct.jem");
    inputs.put(
        custom,
        RIG.replace("\"base\"", "\"bottom\"")
            .replace("\"knob\"", "\"lock\"")
            .replace("\"model\":\"motion.jpm\",", ""));
    // Remove the dependency independently of JSON property order.
    var root = JsonDocuments.object(inputs.files.get(custom));
    root.getAsJsonArray("models").get(0).getAsJsonObject().remove("model");
    inputs.put(custom, root.toString());
    var profile =
        new ProfileCatalogue.Entry(
            new StyleProfile(
                ResourceId.parse("example:direct"), "Direct", Map.of(TARGET, custom), Map.of()),
            inputs,
            "fixture");
    var result =
        ChestPresentation.compile(
            ContainerTargets.LEGACY,
            ClientSettings.Fresh.OFF,
            PRESENT,
            profile,
            new ResourceInputs(inputs),
            new ResourceInputs.Budget(),
            new ArrayList<>());
    assertTrue(result.containsKey(TARGET));
    assertEquals(4, JsonDocuments.object(result.get(TARGET)).getAsJsonArray("models").size());
    assertFalse(inputs.reads.contains(SOURCE));
  }

  @Test
  void unsupportedMaterialLayoutAndAttachmentsAreRefused() {
    for (String rig :
        List.of(
            RIG.replace("[64,64]", "[128,64]"),
            RIG.replace("\"part\":\"base\"", "\"part\":\"unexpected\""),
            RIG.replace("\"credit\":", "\"texture\":\"other.png\",\"credit\":"))) {
      var inputs = inputs();
      inputs.put(SOURCE, rig);
      assertThrows(IllegalArgumentException.class, () -> compile(inputs));
    }
  }

  @Test
  void cyclicMissingAndEscapingReferencesAreRefused() {
    for (String ref : List.of("../motion.jpm", "https:remote.jpm", "missing.jpm", "loop.jpm")) {
      var inputs = inputs();
      inputs.put(SOURCE, RIG.replace("motion.jpm", ref));
      inputs.put(ResourceId.parse("minecraft:emf/cem/loop.jpm"), "{\"model\":\"loop.jpm\"}");
      assertThrows(IllegalArgumentException.class, () -> compile(inputs), ref);
    }
  }

  @Test
  void repeatedDependenciesChargeExpandedCostInsteadOfOnlyUniqueFiles() {
    var inputs = inputs();
    inputs.put(ResourceId.parse("minecraft:emf/cem/motion.jpm"), "{\"model\":\"n0.jpm\"}");
    for (int i = 0; i < 12; i++)
      inputs.put(
          ResourceId.parse("minecraft:emf/cem/n" + i + ".jpm"),
          i == 11
              ? "{}"
              : "{\"submodels\":[{\"model\":\"n"
                  + (i + 1)
                  + ".jpm\"},{\"model\":\"n"
                  + (i + 1)
                  + ".jpm\"}]}");
    var error = assertThrows(IllegalArgumentException.class, () -> compile(inputs));
    assertTrue(error.getMessage().contains("Expanded"));
  }

  @Test
  void remappedAnimationCollisionsAreRefused() {
    var inputs = inputs();
    inputs.put(
        ResourceId.parse("minecraft:emf/cem/motion.jpm"),
        "{\"animations\":[{\"base.tx\":\"1\",\"bottom.tx\":\"2\"}]}");
    assertThrows(IllegalArgumentException.class, () -> compile(inputs));
  }

  @Test
  void nativeFloatOverflowIsRefusedBeforeForeignModelParsing() {
    var inputs = inputs();
    inputs.put(SOURCE, RIG.replace("[3,7]", "[1e39,7]"));
    assertThrows(IllegalArgumentException.class, () -> compile(inputs));
  }

  @Test
  void versionTargetsKeepDistinctNativeRegistryLookupNames() {
    assertEquals("minecraft:emf/cem/lootr_lootr_chest.jem", TARGET.toString());
    assertEquals(
        "minecraft:emf/cem/lootr_chest.jem",
        ContainerTargets.MODERN.chests().getFirst().target().toString());
  }

  @Test
  void cyclesAndRepeatedReferenceAmplificationNeverPublishPartialGraphs() {
    var cycle = inputs();
    cycle.put(
        ResourceId.parse("minecraft:emf/cem/motion.jpm"),
        "{\"submodels\":[{\"model\":\"motion.jpm\"}]}");
    assertThrows(IllegalArgumentException.class, () -> compile(cycle));
    var repeated = inputs();
    repeated.put(
        ResourceId.parse("minecraft:emf/cem/motion.jpm"),
        "{\"submodels\":[{\"model\":\"level0.jpm\"},{\"model\":\"level0.jpm\"}]}");
    for (int i = 0; i < 12; i++)
      repeated.put(
          ResourceId.parse("minecraft:emf/cem/level" + i + ".jpm"),
          i == 11
              ? "{}"
              : "{\"submodels\":[{\"model\":\"level"
                  + (i + 1)
                  + ".jpm\"},{\"model\":\"level"
                  + (i + 1)
                  + ".jpm\"}]}");
    var failure = assertThrows(IllegalArgumentException.class, () -> compile(repeated));
    assertTrue(failure.getMessage().contains("Expanded"));
  }

  @Test
  void escapingReferencesAndAnimationNameCollisionsAreRefused() {
    var escaping = inputs();
    escaping.put(SOURCE, RIG.replace("motion.jpm", "../motion.jpm"));
    assertThrows(IllegalArgumentException.class, () -> compile(escaping));
    var collision = inputs();
    collision.put(
        ResourceId.parse("minecraft:emf/cem/motion.jpm"),
        "{\"animations\":[{\"base.tx\":\"0\",\"bottom.tx\":\"1\"}]}");
    assertThrows(IllegalArgumentException.class, () -> compile(collision));
  }

  @Test
  void modernOrdinaryRigNeedsTheSharedRendererIsolationCapability() {
    var inputs = inputs();
    var status = new ArrayList<String>();
    var without =
        ChestPresentation.compile(
            ContainerTargets.MODERN,
            ClientSettings.Fresh.ON,
            PRESENT,
            inherited(inputs),
            new ResourceInputs(inputs),
            new ResourceInputs.Budget(),
            status,
            false);
    var ordinary = ContainerTargets.MODERN.chests().getFirst().target();
    assertFalse(without.containsKey(ordinary));
    assertTrue(
        status.stream()
            .anyMatch(text -> text.contains("shared copper renderer isolation unavailable")));
    var with =
        ChestPresentation.compile(
            ContainerTargets.MODERN,
            ClientSettings.Fresh.ON,
            PRESENT,
            inherited(inputs),
            new ResourceInputs(inputs),
            new ResourceInputs.Budget(),
            new ArrayList<>(),
            true);
    assertTrue(with.containsKey(ordinary));
  }

  private static final class Memory implements ResourceAccess {
    private final Map<ResourceId, byte[]> files = new HashMap<>();
    private final List<ResourceId> reads = new ArrayList<>();

    void put(ResourceId id, String value) {
      files.put(id, bytes(value));
    }

    @Override
    public Optional<Source> find(ResourceId id) {
      reads.add(id);
      return Optional.ofNullable(files.get(id))
          .map(value -> new Source(Origin.OTHER, "fixture", () -> new ByteArrayInputStream(value)));
    }

    @Override
    public Map<ResourceId, Source> list(String directory) {
      return Map.of();
    }
  }
}
