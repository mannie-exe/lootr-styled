package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.resources.BarrelCompiler;
import design.inherent.lootr_styled.resources.BarrelPreviewInputs;
import design.inherent.lootr_styled.resources.JsonDocuments;
import design.inherent.reform.presentation.graphics.GraphicLedger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** CPU native baking from owned asset bytes; no graphical, GPU, or live application claim. */
class NativeBarrelPreviewTest {
  private static final design.inherent.reform.presentation.graphics.GraphicLimits LIMITS =
      new design.inherent.reform.presentation.graphics.GraphicLimits(
          16 * 1024 * 1024, 16 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);

  private static byte[] resource(ResourceId id) throws Exception {
    try (var stream =
        NativeBarrelPreviewTest.class.getResourceAsStream(
            "/assets/" + id.namespace() + "/" + id.path())) {
      assertNotNull(stream, id.toString());
      return stream.readAllBytes();
    }
  }

  static BarrelPreviewInputs inputs(boolean looted, boolean open, boolean packed) throws Exception {
    return inputs(looted, open, packed, false);
  }

  static BarrelPreviewInputs inputs(boolean looted, boolean open, boolean packed, boolean empty)
      throws Exception {
    return BarrelPreviewInputs.capture(
        NativeUi.targets(),
        new BarrelPreviewInputs.Subject(
            new ClientSettings(
                true,
                ClientSettings.DEFAULT,
                packed ? ClientSettings.DEFAULT : ClientSettings.VANILLA,
                ClientSettings.Fresh.OFF),
            looted,
            open),
        sources(empty),
        null,
        NativeUi::validateModel,
        design.inherent.lootr_styled.resources.EmfSupport.ABSENT,
        false,
        () -> {});
  }

  static design.inherent.lootr_styled.resources.ResourceAccess sources(boolean empty)
      throws Exception {
    var targets = NativeUi.targets();
    var bytes = new HashMap<ResourceId, byte[]>();
    bytes.put(
        ResourceId.parse("minecraft:models/block/block.json"),
        "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    for (var barrel : targets.barrels()) {
      bytes.put(barrel.target(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      for (boolean binding : List.of(false, true)) {
        var model = BarrelCompiler.defaultModel(barrel, binding);
        byte[] modelBytes = resource(model);
        if (empty) {
          var json = JsonDocuments.object(modelBytes);
          json.add("elements", new com.google.gson.JsonArray());
          modelBytes = json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        bytes.put(model, modelBytes);
        if (binding) {
          var json = JsonDocuments.object(modelBytes);
          var name =
              ResourceId.parse(json.getAsJsonObject("textures").get("surface").getAsString());
          var surface = new ResourceId(name.namespace(), "textures/" + name.path() + ".png");
          bytes.put(surface, resource(surface));
        }
      }
    }
    for (var entry : targets.defaultArtwork().entrySet()) {
      bytes.put(entry.getKey(), resource(entry.getValue()));
      bytes.put(entry.getValue(), resource(entry.getValue()));
    }
    var sources =
        new design.inherent.lootr_styled.resources.ResourceAccess() {
          public java.util.Optional<Source> find(ResourceId id) {
            byte[] data = bytes.get(id);
            return data == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(
                    new Source(
                        id.namespace().equals("lootr") ? Origin.LOOTR : Origin.OTHER,
                        "Owned native-bake fixture",
                        () -> new java.io.ByteArrayInputStream(data)));
          }

          public Map<ResourceId, Source> list(String directory) {
            return Map.of();
          }
        };
    return sources;
  }

  @Test
  void copiedNativeFacesPreserveStateCountsLightingTextureIdentityAndFiniteUv() throws Exception {
    for (boolean looted : List.of(false, true))
      for (boolean open : List.of(false, true))
        for (boolean packed : List.of(false, true)) {
          var input = inputs(looted, open, packed);
          var mesh = NativeBarrelPreview.bake(input, new GraphicLedger(LIMITS), () -> {});
          assertEquals(open ? 98 : 76, mesh.faces().size());
          assertFalse(mesh.ambientOcclusion());
          for (var face : mesh.faces()) {
            assertTrue(input.resources().contains(face.texture()));
            assertEquals(-1, face.tintIndex());
            assertEquals(0, face.lightEmission());
            assertTrue(face.shade());
            assertEquals(4, face.vertices().size());
            for (var vertex : face.vertices()) {
              assertTrue(
                  Float.isFinite(vertex.x())
                      && Float.isFinite(vertex.y())
                      && Float.isFinite(vertex.z()));
              assertTrue(vertex.u() >= 0 && vertex.u() <= 1);
              assertTrue(vertex.v() >= 0 && vertex.v() <= 1);
            }
            assertThrows(UnsupportedOperationException.class, () -> face.vertices().clear());
          }
          assertThrows(UnsupportedOperationException.class, () -> mesh.faces().clear());
        }
  }

  @Test
  void nativeBakeSharesTheWorkflowLedgerAndReleasesImagesAfterSuccessOrCancellation()
      throws Exception {
    var input = inputs(false, true, true);
    var ledger = new GraphicLedger(LIMITS);
    long imageBytes = 512L * 512 * Integer.BYTES;
    // An outstanding prior viewport can prevent entering the native image allocation.
    var prior = ledger.reserve(LIMITS.nativeBytes() - imageBytes + 1, 1);
    var before = ledger.state();
    var refused =
        assertThrows(
            design.inherent.reform.owner.PolicyRefusal.class,
            () -> NativeBarrelPreview.bake(input, ledger, () -> {}));
    assertEquals("preview.budget", refused.report().issues().getFirst().code());
    assertEquals(before, ledger.state());
    prior.close();
    assertEquals(98, NativeBarrelPreview.bake(input, ledger, () -> {}).faces().size());
    assertEquals(0, ledger.state().bytes());
    assertEquals(0, ledger.state().resources());

    var cancelledLedger = new GraphicLedger(LIMITS);
    assertThrows(
        CancellationException.class,
        () ->
            NativeBarrelPreview.bake(
                input,
                cancelledLedger,
                () -> {
                  if (cancelledLedger.state().bytes() > 0)
                    throw new CancellationException("Superseded");
                }));
    assertEquals(imageBytes, cancelledLedger.state().peakBytes());
    assertEquals(0, cancelledLedger.state().bytes());
    assertEquals(0, cancelledLedger.state().resources());
    assertEquals(98, NativeBarrelPreview.bake(input, cancelledLedger, () -> {}).faces().size());
  }

  @Test
  void anIntentionallyEmptyNativeModelRemainsEmpty() throws Exception {
    var input = inputs(false, false, true, true);
    var mesh = NativeBarrelPreview.bake(input, new GraphicLedger(LIMITS), () -> {});
    assertTrue(mesh.faces().isEmpty());
    assertSame(input, mesh.inputs());
  }

  @Test
  void cancellationDuringBakingRefusesWithoutPublishingOrMutatingOwnedInputs() throws Exception {
    var input = inputs(false, true, true);
    var before = new HashMap<ResourceId, byte[]>();
    for (var id : input.resources().ids())
      try (var stream = input.resources().open(id).orElseThrow()) {
        before.put(id, stream.readAllBytes());
      }
    var checkpoints = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            NativeBarrelPreview.bake(
                input,
                new GraphicLedger(LIMITS),
                () -> {
                  if (checkpoints.incrementAndGet() == 4)
                    throw new CancellationException("replaced");
                }));
    assertEquals(
        98, NativeBarrelPreview.bake(input, new GraphicLedger(LIMITS), () -> {}).faces().size());
    for (var entry : before.entrySet())
      try (var stream = input.resources().open(entry.getKey()).orElseThrow()) {
        assertArrayEquals(entry.getValue(), stream.readAllBytes());
      }
  }
}
