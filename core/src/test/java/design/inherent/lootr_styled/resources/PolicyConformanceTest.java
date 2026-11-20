package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Reads evaluated Bend vectors, then drives the actual Java compiler on every native Gson lane. */
class PolicyConformanceTest {
  @Test
  void actualCompilerAgreesWithTheExecutableDecisionModel() throws Exception {
    String vectors = System.getProperty("lootr.styled.proofVectors");
    java.util.List<String> rows;
    if (vectors != null) rows = Files.readAllLines(Path.of(vectors));
    else
      try (var input = getClass().getResourceAsStream("/proofs/source-vectors.csv")) {
        assertNotNull(input, "Missing evaluated decision-model vectors");
        rows = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
      }
    assertEquals(33, rows.size());
    for (String row : rows.subList(1, rows.size())) {
      String[] values = row.split(",");
      boolean enabled = Boolean.parseBoolean(values[0]);
      boolean custom = Boolean.parseBoolean(values[1]);
      boolean explicit = Boolean.parseBoolean(values[2]);
      boolean styled = Boolean.parseBoolean(values[3]);
      boolean inherited = Boolean.parseBoolean(values[4]);
      int expected = Integer.parseInt(values[5]);
      int observed = actual(enabled, custom, explicit, styled, inherited);
      assertEquals(expected, observed, row);
    }
  }

  @Test
  void aDeliberateVectorDisagreementIsDetected() throws Exception {
    int observed = actual(true, false, true, true, true);
    assertEquals(2, observed);
    assertThrows(
        AssertionError.class,
        () -> assertEquals(4, observed, "mutated model would put Default above explicit"));
  }

  private static int actual(
      boolean enabled, boolean custom, boolean explicit, boolean styled, boolean inherited)
      throws Exception {
    var target = ContainerTargets.LEGACY.barrels().getFirst();
    var resources = new HashMap<ResourceId, ResourceAccess.Source>();
    put(
        resources,
        target.target(),
        "{}",
        explicit ? ResourceAccess.Origin.OTHER : ResourceAccess.Origin.LOOTR);
    if (inherited) put(resources, target.vanilla(), cube(1), ResourceAccess.Origin.MINECRAFT);
    if (styled) put(resources, BarrelCompiler.DEFAULT_MODEL, cube(4), ResourceAccess.Origin.OTHER);
    var descriptor = ResourceId.parse("example:lootr_styled/profiles/selected.json");
    if (custom) {
      put(
          resources,
          descriptor,
          "{\"format\":1,\"models\":{\"" + target.target() + "\":\"example:models/custom.json\"}}",
          ResourceAccess.Origin.OTHER);
      put(
          resources,
          ResourceId.parse("example:models/custom.json"),
          cube(3),
          ResourceAccess.Origin.OTHER);
    }
    var access =
        new ResourceAccess() {
          @Override
          public Optional<Source> find(ResourceId id) {
            return Optional.ofNullable(resources.get(id));
          }

          @Override
          public Map<ResourceId, Source> list(String directory) {
            return custom ? Map.of(descriptor, resources.get(descriptor)) : Map.of();
          }
        };
    var selected =
        custom
            ? ResourceId.parse("example:selected")
            : styled ? ClientSettings.DEFAULT : ClientSettings.VANILLA;
    var settings =
        new ClientSettings(enabled, selected, ClientSettings.VANILLA, ClientSettings.Fresh.OFF);
    var result =
        PresentationCompiler.compile(
            ContainerTargets.LEGACY,
            settings,
            access,
            null,
            b -> BlockModels.validate(JsonDocuments.object(b)));
    if (!enabled) return 0;
    var output = result.resources().open(target.target());
    if (output.isEmpty()) return explicit ? 2 : 0;
    try (var stream = output.orElseThrow()) {
      return JsonDocuments.object(stream.readAllBytes())
          .getAsJsonArray("elements")
          .get(0)
          .getAsJsonObject()
          .getAsJsonArray("from")
          .get(0)
          .getAsInt();
    }
  }

  private static String cube(int marker) {
    return "{\"elements\":[{\"from\":[" + marker + ",1,1],\"to\":[" + (marker + 1) + ",2,2]}]}";
  }

  private static void put(
      Map<ResourceId, ResourceAccess.Source> resources,
      ResourceId id,
      String value,
      ResourceAccess.Origin origin) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    resources.put(
        id,
        new ResourceAccess.Source(origin, id.toString(), () -> new ByteArrayInputStream(bytes)));
  }
}
