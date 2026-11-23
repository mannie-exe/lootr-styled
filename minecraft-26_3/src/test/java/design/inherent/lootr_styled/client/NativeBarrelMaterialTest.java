package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.resources.BarrelPreviewInputs;
import design.inherent.lootr_styled.resources.BarrelPreviewMesh;
import design.inherent.lootr_styled.resources.EmfSupport;
import design.inherent.lootr_styled.resources.ResourceAccess;
import design.inherent.reform.presentation.graphics.GraphicLedger;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Actual modern native material selection from frozen PNG/animation metadata. */
class NativeBarrelMaterialTest {
  private static final GraphicLimits LIMITS =
      new GraphicLimits(
          16 * 1024 * 1024, 16 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);

  @Test
  void perFaceUvTransparencyDoesNotUseTheWholeSpriteTransparency() throws Exception {
    var image =
        new java.awt.image.BufferedImage(32, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 32; x++) image.setRGB(x, y, x < 16 ? 0xffaabbcc : 0x80aabbcc);
    var input = fixture(image, Map.of(), false);
    var result = NativeBarrelPreview.bake(input, new GraphicLedger(LIMITS), () -> {});
    assertEquals(2, result.faces().size());
    assertEquals(
        BarrelPreviewMesh.Layer.SOLID,
        result.faces().stream()
            .filter(f -> f.direction().equals("north"))
            .findFirst()
            .orElseThrow()
            .nativeLayer()
            .orElseThrow());
    assertEquals(
        BarrelPreviewMesh.Layer.TRANSLUCENT,
        result.faces().stream()
            .filter(f -> f.direction().equals("south"))
            .findFirst()
            .orElseThrow()
            .nativeLayer()
            .orElseThrow());
  }

  @Test
  void identicalPngsWithDifferentDeclaredFramesDoNotAliasTheirNativeMaterialState()
      throws Exception {
    var image =
        new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 64; x++) image.setRGB(x, y, x < 32 ? 0xffaabbcc : 0x80aabbcc);
    var input = fixture(image, Map.of("a", 0, "b", 1), true);
    var ledger = new GraphicLedger(LIMITS);
    var result = NativeBarrelPreview.bake(input, ledger, () -> {});
    assertEquals(
        BarrelPreviewMesh.Layer.SOLID,
        result.faces().stream()
            .filter(f -> f.direction().equals("north"))
            .findFirst()
            .orElseThrow()
            .nativeLayer()
            .orElseThrow());
    assertEquals(
        BarrelPreviewMesh.Layer.TRANSLUCENT,
        result.faces().stream()
            .filter(f -> f.direction().equals("south"))
            .findFirst()
            .orElseThrow()
            .nativeLayer()
            .orElseThrow());
    assertEquals(2, ledger.state().peakResources());
    assertEquals(0, ledger.state().resources());
    assertEquals(0, ledger.state().bytes());
  }

  @Test
  void nativeSingleFrameShortcutIsDistinctFromTheDeclaredStillFrameContract() throws Exception {
    var image =
        new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 64; x++) image.setRGB(x, y, x < 32 ? 0xffaabbcc : 0x80aabbcc);
    var input = fixture(image, Map.of("a", 0, "b", 1), true, false);
    var result = NativeBarrelPreview.bake(input, new GraphicLedger(LIMITS), () -> {});
    // Native SpriteContents drops AnimatedTexture for a one-entry list and classifies frame0.
    assertTrue(
        result.faces().stream()
            .allMatch(f -> f.nativeLayer().orElseThrow() == BarrelPreviewMesh.Layer.SOLID));
    // The shared still contract uses the first declared index. Keep these facts distinct.
    var bundle =
        StylePreviewMesh.convert(
            result,
            LIMITS,
            id ->
                new design.inherent.reform.presentation.graphics.GraphicMaterial(
                    id,
                    design.inherent.reform.presentation.graphics.GraphicMaterial.Alpha.CUTOUT,
                    0.1f,
                    design.inherent.reform.presentation.graphics.GraphicMaterial.Cull.BACK,
                    design.inherent.reform.presentation.graphics.GraphicMaterial.Filter.NEAREST,
                    design.inherent.reform.presentation.graphics.GraphicMaterial.Wrap.CLAMP),
            () -> {});
    var scene =
        bundle.prepare(
            new GraphicLedger(LIMITS),
            new design.inherent.reform.serialization.JsonLimits(16384, 32, 4096, 8192, 256, 128),
            () -> {});
    for (var entry : bundle.sources().entrySet()) {
      int expected = entry.getValue().path().endsWith("b.png") ? 128 : 255;
      assertEquals(expected, scene.textures().get(entry.getKey()).pixel(0, 0) >>> 24);
    }
  }

  private static BarrelPreviewInputs fixture(
      java.awt.image.BufferedImage image, Map<String, Integer> frames, boolean separate)
      throws Exception {
    return fixture(image, frames, separate, true);
  }

  private static BarrelPreviewInputs fixture(
      java.awt.image.BufferedImage image,
      Map<String, Integer> frames,
      boolean separate,
      boolean repeatFrames)
      throws Exception {
    var buffer = new java.io.ByteArrayOutputStream();
    assertTrue(javax.imageio.ImageIO.write(image, "PNG", buffer));
    var assets = new java.util.HashMap<ResourceId, byte[]>();
    var textures = new com.google.gson.JsonObject();
    textures.addProperty("a", "example:a");
    textures.addProperty("b", separate ? "example:b" : "example:a");
    for (String name : java.util.List.of("a", "b")) {
      assets.put(ResourceId.parse("example:textures/" + name + ".png"), buffer.toByteArray());
      if (frames.containsKey(name))
        assets.put(
            ResourceId.parse("example:textures/" + name + ".png.mcmeta"),
            ("{\"animation\":{\"width\":32,\"height\":32,\"frames\":["
                    + frames.get(name)
                    + (repeatFrames ? "," + frames.get(name) : "")
                    + "]}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    var faces = new com.google.gson.JsonObject();
    faces.add(
        "north", com.google.gson.JsonParser.parseString("{\"texture\":\"#a\",\"uv\":[0,0,8,16]}"));
    faces.add(
        "south",
        com.google.gson.JsonParser.parseString(
            separate
                ? "{\"texture\":\"#b\",\"uv\":[0,0,8,16]}"
                : "{\"texture\":\"#a\",\"uv\":[8,0,16,16]}"));
    var element =
        com.google.gson.JsonParser.parseString("{\"from\":[0,0,0],\"to\":[16,16,16]}")
            .getAsJsonObject();
    element.add("faces", faces);
    var elements = new com.google.gson.JsonArray();
    elements.add(element);
    var model = new com.google.gson.JsonObject();
    model.addProperty("ambientocclusion", false);
    model.add("textures", textures);
    model.add("elements", elements);
    assets.put(
        ResourceId.parse("example:models/block/material.json"),
        model.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var mappings = new com.google.gson.JsonObject();
    for (var barrel : NativeUi.targets().barrels())
      mappings.addProperty(barrel.target().toString(), "example:models/block/material.json");
    var profile = new com.google.gson.JsonObject();
    profile.addProperty("format", 1);
    profile.add("models", mappings);
    assets.put(
        ResourceId.parse("example:lootr_styled/profiles/material.json"),
        profile.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var base = NativeBarrelPreviewTest.sources(false);
    var access =
        new ResourceAccess() {
          public Optional<Source> find(ResourceId id) {
            var bytes = assets.get(id);
            return bytes == null
                ? base.find(id)
                : Optional.of(
                    new Source(
                        Origin.OTHER,
                        "Material fixture",
                        () -> new java.io.ByteArrayInputStream(bytes)));
          }

          public Map<ResourceId, Source> list(String directory) {
            var result = new java.util.HashMap<>(base.list(directory));
            assets.forEach(
                (id, bytes) -> {
                  if (id.path().startsWith(directory)) result.put(id, find(id).orElseThrow());
                });
            return result;
          }
        };
    return BarrelPreviewInputs.capture(
        NativeUi.targets(),
        new BarrelPreviewInputs.Subject(
            new ClientSettings(
                true,
                ResourceId.parse("example:material"),
                ClientSettings.VANILLA,
                ClientSettings.Fresh.OFF),
            false,
            false),
        access,
        null,
        NativeUi::validateModel,
        EmfSupport.ABSENT,
        false,
        () -> {});
  }
}
