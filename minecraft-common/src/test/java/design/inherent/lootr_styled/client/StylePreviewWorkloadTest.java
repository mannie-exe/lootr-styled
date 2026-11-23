package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.resources.*;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** CPU workload observations; no timing threshold, frame-rate, GPU, or native UI claim. */
class StylePreviewWorkloadTest {
  private static final GraphicLimits LIMITS =
      new GraphicLimits(
          16 * 1024 * 1024, 32 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
  private static final ResourceId TEXTURE = ResourceId.parse("workload:textures/surface.png");

  private static GraphicMaterial material(design.inherent.reform.definition.DocumentId id) {
    return StyleStillMaterials.material(id, GraphicMaterial.Alpha.OPAQUE);
  }

  private static byte[] png(int width, int height) throws IOException {
    var image =
        new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    try {
      graphics.setColor(new java.awt.Color(120, 90, 50, 255));
      graphics.fillRect(0, 0, width, height);
      var out = new ByteArrayOutputStream();
      assertTrue(javax.imageio.ImageIO.write(image, "png", out));
      return out.toByteArray();
    } finally {
      graphics.dispose();
      image.flush();
    }
  }

  private static BarrelPreviewInputs inputs(int cuboids, byte[] png) throws Exception {
    var model = new JsonObject();
    model.addProperty("ambientocclusion", false);
    var textures = new JsonObject();
    textures.addProperty("surface", "workload:surface");
    model.add("textures", textures);
    var elements = new JsonArray();
    for (int i = 0; i < cuboids; i++) {
      int x = i % 16, z = i / 16 % 16, y = i / 256;
      var element = new JsonObject();
      element.add("from", new Gson().toJsonTree(new int[] {x, y, z}));
      element.add("to", new Gson().toJsonTree(new int[] {x + 1, y + 1, z + 1}));
      var faces = new JsonObject();
      for (String direction : List.of("up", "down", "north", "south", "east", "west")) {
        var face = new JsonObject();
        face.addProperty("texture", "#surface");
        face.add("uv", new Gson().toJsonTree(new int[] {0, 0, 16, 16}));
        faces.add(direction, face);
      }
      element.add("faces", faces);
      elements.add(element);
    }
    model.add("elements", elements);
    byte[] encoded = model.toString().getBytes(StandardCharsets.UTF_8);
    var targets = NativeUi.targets();
    var base = NativeBarrelPreviewTest.sources(false);
    ResourceAccess resources =
        new ResourceAccess() {
          public Optional<Source> find(ResourceId id) {
            byte[] value =
                id.equals(TEXTURE)
                    ? png
                    : targets.barrels().stream().anyMatch(b -> b.target().equals(id))
                        ? encoded
                        : null;
            return value == null
                ? base.find(id)
                : Optional.of(
                    new Source(
                        Origin.OTHER,
                        "Owned synthetic workload",
                        () -> new ByteArrayInputStream(value)));
          }

          public Map<ResourceId, Source> list(String directory) {
            return Map.of();
          }
        };
    long started = System.nanoTime();
    var captured =
        BarrelPreviewInputs.capture(
            targets,
            new BarrelPreviewInputs.Subject(
                new ClientSettings(
                    true, ClientSettings.VANILLA, ClientSettings.VANILLA, ClientSettings.Fresh.OFF),
                false,
                false),
            resources,
            null,
            NativeUi::validateModel,
            EmfSupport.ABSENT,
            false,
            () -> {});
    var row = new JsonObject();
    row.addProperty("cuboids", cuboids);
    row.addProperty("captureNanos", System.nanoTime() - started);
    row.addProperty("encodedTextureBytes", png.length);
    row.addProperty("capturedBytes", captured.resources().byteSize());
    System.out.println("LOOTR_PREVIEW_CAPTURE " + row);
    return captured;
  }

  private static JsonObject sample(BarrelPreviewInputs input) throws Exception {
    var ledger = new GraphicLedger(LIMITS);
    var checkpoints = new AtomicInteger();
    long start = System.nanoTime();
    var baked = NativeBarrelPreview.bake(input, ledger, checkpoints::incrementAndGet);
    long bakedAt = System.nanoTime();
    var mesh =
        StylePreviewMesh.convert(
            baked, LIMITS, StylePreviewWorkloadTest::material, checkpoints::incrementAndGet);
    long convertedAt = System.nanoTime();
    var still = mesh.prepare(ledger, StyleGraphicProvider.METADATA, checkpoints::incrementAndGet);
    long decodedAt = System.nanoTime();
    still = StyleStillMaterials.classify(still, LIMITS, checkpoints::incrementAndGet);
    var result = StyleReferenceLighting.prepare(still, LIMITS, checkpoints::incrementAndGet);
    long finishedAt = System.nanoTime();
    assertEquals(baked.faces().size() * 2, result.scene().mesh().triangles().size());
    assertEquals(0, ledger.state().bytes());
    assertEquals(0, ledger.state().resources());
    var row = new JsonObject();
    row.addProperty("faces", baked.faces().size());
    row.addProperty("inputBytes", input.resources().byteSize());
    row.addProperty("geometryBytes", mesh.mesh().geometryBytes());
    row.addProperty("trackedNativePeakBytes", ledger.state().peakBytes());
    row.addProperty("checkpoints", checkpoints.get());
    row.addProperty("bakeNanos", bakedAt - start);
    row.addProperty("convertNanos", convertedAt - bakedAt);
    row.addProperty("decodeNanos", decodedAt - convertedAt);
    row.addProperty("classifyAndLightingNanos", finishedAt - decodedAt);
    return row;
  }

  @Test
  void defaultAndMaximumCuboidWorkloadsRetainCompleteGeometryAndBoundedRetirement()
      throws Exception {
    var rows = new JsonArray();
    for (var input :
        List.of(NativeBarrelPreviewTest.inputs(false, true, true), inputs(1024, png(16, 16)))) {
      sample(input); // Separate warmup, excluded from retained measurements.
      for (int i = 0; i < 5; i++) rows.add(sample(input));
    }
    assertEquals(98, rows.get(0).getAsJsonObject().get("faces").getAsInt());
    assertEquals(6144, rows.get(5).getAsJsonObject().get("faces").getAsInt());
    System.out.println("LOOTR_PREVIEW_WORKLOAD " + rows);
  }

  @Test
  void maximumModelCancellationAndGeometryRefusalLeaveNoNativeOwnership() throws Exception {
    var input = inputs(1024, png(16, 16));
    var ledger = new GraphicLedger(LIMITS);
    var checks = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            NativeBarrelPreview.bake(
                input,
                ledger,
                () -> {
                  if (checks.incrementAndGet() == 3000)
                    throw new CancellationException("Replaced large workload");
                }));
    assertEquals(3000, checks.get());
    assertEquals(0, ledger.state().bytes());
    assertEquals(0, ledger.state().resources());
    var baked = NativeBarrelPreview.bake(input, ledger, () -> {});
    assertEquals(6144, baked.faces().size());
    var exact =
        StylePreviewMesh.convert(baked, LIMITS, StylePreviewWorkloadTest::material, () -> {});
    var small =
        new GraphicLimits(
            LIMITS.inputBytes(),
            exact.mesh().geometryBytes() - 1,
            LIMITS.decodedPixels(),
            LIMITS.resources(),
            LIMITS.nativeBytes());
    var refusal =
        assertThrows(
            PolicyRefusal.class,
            () ->
                StylePreviewMesh.convert(
                    baked, small, StylePreviewWorkloadTest::material, () -> {}));
    assertEquals("preview.budget", refusal.report().issues().getFirst().code());
  }

  @Test
  void acceptedMaximumTextureGetsAnExplicitStillOutputRefusalAndReleasesItsReservation()
      throws Exception {
    long start = System.nanoTime();
    var input = inputs(1, png(4096, 4096));
    long captured = System.nanoTime();
    assertEquals(new TextureImages.Size(4096, 4096), input.imageSizes().get(TEXTURE));
    var ledger = new GraphicLedger(LIMITS);
    var baked = NativeBarrelPreview.bake(input, ledger, () -> {});
    long bakedAt = System.nanoTime();
    assertEquals(4096L * 4096 * Integer.BYTES, ledger.state().peakBytes());
    assertEquals(0, ledger.state().bytes());
    var mesh =
        StylePreviewMesh.convert(baked, LIMITS, StylePreviewWorkloadTest::material, () -> {});
    var stillLedger = new GraphicLedger(LIMITS);
    var refusal =
        assertThrows(
            PolicyRefusal.class,
            () -> mesh.prepare(stillLedger, StyleGraphicProvider.METADATA, () -> {}));
    assertEquals("preview.budget", refusal.report().issues().getFirst().code());
    // The shared batch owns a temporary reservation before decodeStill's frame/output preflight.
    // A reservation peak is not evidence that the native decoder actually allocated that image.
    assertEquals(4096L * 4096 * Integer.BYTES, stillLedger.state().peakBytes());
    assertEquals(0, stillLedger.state().bytes());
    assertEquals(0, stillLedger.state().resources());
    var row = new JsonObject();
    row.addProperty("captureAndPngGenerationNanos", captured - start);
    row.addProperty("bakeNanos", bakedAt - captured);
    row.addProperty("nativeBakePeakBytes", ledger.state().peakBytes());
    row.addProperty("stillReservationPeakBytes", stillLedger.state().peakBytes());
    row.addProperty("refusalCode", refusal.report().issues().getFirst().code());
    System.out.println("LOOTR_PREVIEW_TEXTURE_LIMIT " + row);
  }
}
