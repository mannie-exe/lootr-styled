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
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

/** Actual native baking, UV contraction and mip generation; no GL or copied native equations. */
class NativeBarrelSamplingTest {
  private static final class Sprite extends TextureAtlasSprite {
    Sprite(SpriteContents contents) {
      super(
          Identifier.parse("minecraft:textures/atlas/blocks.png"),
          contents,
          BarrelSampling.ATLAS_WIDTH,
          BarrelSampling.ATLAS_HEIGHT,
          0,
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
            moved.setPixel((x + shift) % image.getWidth(), y, image.getPixel(x, y));
        image.close();
        image = moved;
      }
      sprite =
          new Sprite(
              new SpriteContents(
                  Identifier.parse(id.toString()),
                  new FrameSize(image.getWidth(), image.getHeight()),
                  image));
      mips =
          MipmapGenerator.generateMipLevels(
              Identifier.parse(id.toString()),
              new NativeImage[] {image},
              4,
              MipmapStrategy.AUTO,
              0f,
              image.computeTransparency());
    }

    public int color(int level, int x, int y) {
      return mips[level].getPixel(x, y);
    }

    public int width(int level) {
      return mips[level].getWidth();
    }

    public int height(int level) {
      return mips[level].getHeight();
    }

    // AUTO selects CUTOUT for the padded sprite. Native coverage preservation can rescale
    // even solid texels; the renderer's alpha cutoff, rather than alpha=255, governs visibility.
    public boolean visible(int color) {
      return color >>> 24 >= 128;
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
      var model = CuboidModel.fromStream(new java.io.StringReader(json.toString()));
      var interner =
          new ModelBaker.Interner() {
            public Vector3fc vector(Vector3fc v) {
              return v;
            }

            public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo m) {
              return m;
            }
          };
      for (var element : ((UnbakedCuboidGeometry) model.geometry()).elements())
        for (var entry : element.faces().entrySet()) {
          var id = BarrelSampling.image(json, entry.getValue().texture(), identicalTops);
          Images pixels = images.get(id);
          if (pixels == null) {
            pixels = new Images(id, shift);
            images.put(id, pixels);
          }
          var face = entry.getValue();
          var uvs = face.uvs();
          if (shift != 0) {
            float offset = 16f * shift / pixels.width(0);
            uvs =
                new net.minecraft.client.resources.model.cuboid.CuboidFace.UVs(
                    uvs.minU() + offset, uvs.minV(), uvs.maxU() + offset, uvs.maxV());
          }
          var material =
              new BakedQuad.MaterialInfo(pixels.sprite, null, null, null, null, -1, null, 0);
          var q =
              FaceBakery.bakeQuad(
                  interner,
                  element.from(),
                  element.to(),
                  uvs,
                  face.rotation(),
                  material,
                  entry.getKey(),
                  new ModelState() {},
                  element.rotation());
          float[][] p = new float[4][3], uv = new float[4][2];
          for (int v = 0; v < 4; v++) {
            p[v][0] = q.position(v).x();
            p[v][1] = q.position(v).y();
            p[v][2] = q.position(v).z();
            uv[v][0] = UVPair.unpackU(q.packedUV(v));
            uv[v][1] = UVPair.unpackV(q.packedUV(v));
          }
          quads.add(new BarrelSampling.Quad(q.direction().getName(), p, uv, pixels));
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
