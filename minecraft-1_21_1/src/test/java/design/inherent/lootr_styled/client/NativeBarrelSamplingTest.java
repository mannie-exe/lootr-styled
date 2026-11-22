package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.NativeImage;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.BarrelSampling;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

/** Actual native baking, UV contraction and mip generation; no GL or copied native equations. */
class NativeBarrelSamplingTest {
  private static final class Sprite extends TextureAtlasSprite {
    Sprite(SpriteContents contents) {
      super(
          ResourceLocation.parse("minecraft:textures/atlas/blocks.png"),
          contents,
          BarrelSampling.ATLAS_WIDTH,
          BarrelSampling.ATLAS_HEIGHT,
          0,
          0);
    }
  }

  private static final class Images implements BarrelSampling.Pixels, AutoCloseable {
    final NativeImage[] mips;
    final Sprite sprite;

    Images(ResourceId id, int shift) throws Exception {
      var image = NativeImage.read(new ByteArrayInputStream(BarrelSampling.resource(id)));
      if (shift != 0) {
        var moved = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++)
          for (int x = 0; x < image.getWidth(); x++)
            moved.setPixelRGBA((x + shift) % image.getWidth(), y, image.getPixelRGBA(x, y));
        image.close();
        image = moved;
      }
      sprite =
          new Sprite(
              new SpriteContents(
                  ResourceLocation.parse(id.toString()),
                  new FrameSize(image.getWidth(), image.getHeight()),
                  image,
                  ResourceMetadata.EMPTY));
      mips = MipmapGenerator.generateMipLevels(new NativeImage[] {image}, 4);
    }

    public int color(int level, int x, int y) {
      return mips[level].getPixelRGBA(x, y);
    }

    public int width(int level) {
      return mips[level].getWidth();
    }

    public int height(int level) {
      return mips[level].getHeight();
    }

    public void close() {
      for (var image : mips) image.close();
    }
  }

  private static final class Model implements AutoCloseable {
    final Map<ResourceId, Images> images = new HashMap<>();
    final List<BarrelSampling.Quad> quads = new ArrayList<>();

    @SuppressWarnings("deprecation")
    Model(boolean looted, boolean open, boolean packed, boolean identicalTops, int shift)
        throws Exception {
      var json = BarrelSampling.model(looted, open, packed);
      var model = BlockModel.fromString(json.toString());
      var baker = new FaceBakery();
      for (var element : model.getElements())
        for (var entry : element.faces.entrySet()) {
          var id = BarrelSampling.image(json, entry.getValue().texture(), identicalTops);
          Images pixels = images.get(id);
          if (pixels == null) {
            pixels = new Images(id, shift);
            images.put(id, pixels);
          }
          var face = entry.getValue();
          if (shift != 0) {
            float offset = 16f * shift / pixels.width(0);
            face.uv().uvs[0] += offset;
            face.uv().uvs[2] += offset;
          }
          var q =
              baker.bakeQuad(
                  element.from,
                  element.to,
                  face,
                  pixels.sprite,
                  entry.getKey(),
                  BlockModelRotation.X0_Y0,
                  element.rotation,
                  element.shade);
          var data = q.getVertices();
          float[][] p = new float[4][3], uv = new float[4][2];
          for (int v = 0; v < 4; v++) {
            for (int k = 0; k < 3; k++) p[v][k] = Float.intBitsToFloat(data[v * 8 + k]);
            uv[v][0] = Float.intBitsToFloat(data[v * 8 + 4]);
            uv[v][1] = Float.intBitsToFloat(data[v * 8 + 5]);
          }
          quads.add(new BarrelSampling.Quad(q.getDirection().getName(), p, uv, pixels));
        }
    }

    public void close() {
      images.values().forEach(Images::close);
    }
  }

  @Test
  void nativeBakedPersistentSurfacesMatchAtCentersAndBoundariesThroughMipFour() throws Exception {
    for (boolean looted : List.of(false, true))
      for (boolean packed : List.of(false, true))
        try (var c = new Model(looted, false, packed, true, 0);
            var o = new Model(looted, true, packed, true, 0)) {
          for (int level = 0; level <= 4; level++)
            assertEquals(
                0,
                BarrelSampling.differences(c.quads, o.quads, level, true),
                "looted=" + looted + " packed=" + packed + " mip=" + level);
        }
  }

  @Test
  void installedNativeArtworkKeepsWallsStableWithoutChangingItsOpenTop() throws Exception {
    for (boolean looted : List.of(false, true))
      try (var c = new Model(looted, false, false, false, 0);
          var o = new Model(looted, true, false, false, 0)) {
        for (int level = 0; level <= 4; level++)
          assertEquals(0, BarrelSampling.differences(c.quads, o.quads, level, false));
      }
  }

  @Test
  void shiftedMipGridNegativeKeepsBaseTexelsButChangesFilteredAppearance() throws Exception {
    try (var c = new Model(false, false, true, false, 0);
        var o = new Model(false, true, true, false, 1)) {
      assertEquals(0, BarrelSampling.differences(c.quads, o.quads, 0, true));
      assertTrue(BarrelSampling.differences(c.quads, o.quads, 4, true) > 0);
    }
  }
}
