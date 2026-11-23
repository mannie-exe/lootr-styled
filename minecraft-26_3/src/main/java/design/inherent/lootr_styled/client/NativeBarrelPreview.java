package design.inherent.lootr_styled.client;

import com.mojang.blaze3d.platform.NativeImage;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.BarrelPreviewInputs;
import design.inherent.lootr_styled.resources.BarrelPreviewMesh;
import design.inherent.lootr_styled.resources.JsonDocuments;
import design.inherent.lootr_styled.resources.TextureImages;
import design.inherent.reform.presentation.graphics.GraphicLedger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

/** Native CPU baking only. Shared re:form graphics own private GPU upload and submission. */
final class NativeBarrelPreview {
  private NativeBarrelPreview() {}

  private static final class Sprite extends TextureAtlasSprite {
    Sprite(SpriteContents contents) {
      super(
          Identifier.parse("lootr_styled:isolated_preview"),
          contents,
          contents.width(),
          contents.height(),
          0,
          0,
          0);
    }
  }

  private static final class Images implements AutoCloseable {
    private final BarrelPreviewInputs inputs;
    private final GraphicLedger ledger;
    private final Map<String, GraphicLedger.Allocation> allocations = new HashMap<>();
    private final Map<String, SpriteContents> owned = new HashMap<>();
    private final Map<ResourceId, Sprite> sprites = new HashMap<>();
    private long decodedPixels;

    Images(BarrelPreviewInputs inputs, GraphicLedger ledger) {
      this.inputs = inputs;
      this.ledger = ledger;
    }

    Sprite sprite(ResourceId id) throws IOException {
      var cached = sprites.get(id);
      if (cached != null) return cached;
      byte[] bytes;
      try (var stream = inputs.resources().open(id).orElseThrow()) {
        bytes = stream.readAllBytes();
      }
      var metadata = new ResourceId(id.namespace(), id.path() + ".mcmeta");
      byte[] meta = new byte[0];
      if (inputs.resources().contains(metadata))
        try (var stream = inputs.resources().open(metadata).orElseThrow()) {
          meta = stream.readAllBytes();
        }
      var size =
          java.util.Objects.requireNonNull(
              inputs.imageSizes().get(id), "Unvalidated preview texture");
      var frame = new FrameSize(size.width(), size.height());
      if (meta.length > 0) {
        var json = JsonDocuments.object(meta);
        if (json.has("animation"))
          frame =
              AnimationMetadataSection.CODEC
                  .parse(com.mojang.serialization.JsonOps.INSTANCE, json.get("animation"))
                  .getOrThrow()
                  .calculateFrameSize(size.width(), size.height());
      }
      String key = TextureImages.hash(bytes) + ":" + frame.width() + ":" + frame.height();
      var contents = owned.get(key);
      if (contents == null) {
        long area = (long) size.width() * size.height();
        if (area
            > Math.min(TextureImages.MAX_PIXELS, ledger.limits().decodedPixels()) - decodedPixels)
          throw new design.inherent.reform.owner.PolicyRefusal(
              "preview.budget", "The barrel textures exceed the preview decoded-pixel allowance");
        var allocation = ledger.reserve(area * Integer.BYTES, 1);
        NativeImage image = null;
        boolean transferred = false;
        try {
          image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
          if (image.getWidth() != size.width() || image.getHeight() != size.height())
            throw new IllegalArgumentException("Native preview image dimensions changed");
          contents = new SpriteContents(Identifier.parse(id.toString()), frame, image);
          owned.put(key, contents);
          allocations.put(key, allocation);
          decodedPixels += area;
          transferred = true;
        } finally {
          if (!transferred) {
            if (image != null) image.close();
            allocation.close();
          }
        }
      }
      var sprite = new Sprite(contents);
      sprites.put(id, sprite);
      return sprite;
    }

    @Override
    public void close() {
      RuntimeException failed = null;
      for (var entry : owned.entrySet()) {
        try {
          entry.getValue().close();
          allocations.get(entry.getKey()).close();
        } catch (RuntimeException error) {
          if (failed == null) failed = error;
          else failed.addSuppressed(error);
        }
      }
      sprites.clear();
      owned.clear();
      allocations.clear();
      if (failed != null) throw failed;
    }
  }

  @SuppressWarnings("deprecation")
  static BarrelPreviewMesh bake(
      BarrelPreviewInputs inputs, GraphicLedger ledger, Runnable checkpoint) throws IOException {
    checkpoint.run();
    if (inputs.resources().byteSize() > ledger.limits().inputBytes())
      throw new design.inherent.reform.owner.PolicyRefusal(
          "preview.budget", "The captured barrel exceeds the preview input allowance");
    byte[] bytes;
    try (var stream = inputs.resources().open(inputs.barrel().target()).orElseThrow()) {
      bytes = stream.readAllBytes();
    }
    var json = JsonDocuments.object(bytes);
    var model =
        CuboidModel.fromStream(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8)));
    if (!(model.geometry() instanceof UnbakedCuboidGeometry geometry))
      throw new IllegalArgumentException("Unsupported isolated barrel geometry");
    var faces = new ArrayList<BarrelPreviewMesh.Face>();
    var interner =
        new ModelBaker.Interner() {
          public Vector3fc vector(Vector3fc vector) {
            return vector;
          }

          public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return material;
          }
        };
    try (var images = new Images(inputs, ledger)) {
      for (var element : geometry.elements())
        for (var entry : element.faces().entrySet()) {
          checkpoint.run();
          var face = entry.getValue();
          var texture = BarrelPreviewMesh.texture(json, face.texture());
          var material =
              new BakedQuad.MaterialInfo(
                  images.sprite(texture),
                  null,
                  null,
                  null,
                  null,
                  face.tintIndex(),
                  element.shadeDirectionOverride(),
                  element.lightEmission());
          var quad =
              FaceBakery.bakeQuad(
                  interner,
                  element.from(),
                  element.to(),
                  face.uvs(),
                  face.rotation(),
                  material,
                  entry.getKey(),
                  new ModelState() {},
                  element.rotation());
          var vertices = new ArrayList<BarrelPreviewMesh.Vertex>();
          for (int vertex = 0; vertex < 4; vertex++)
            vertices.add(
                new BarrelPreviewMesh.Vertex(
                    quad.position(vertex).x(),
                    quad.position(vertex).y(),
                    quad.position(vertex).z(),
                    UVPair.unpackU(quad.packedUV(vertex)),
                    UVPair.unpackV(quad.packedUV(vertex))));
          faces.add(
              new BarrelPreviewMesh.Face(
                  texture,
                  List.copyOf(vertices),
                  quad.direction().getName(),
                  true,
                  Optional.ofNullable(element.shadeDirectionOverride())
                      .map(net.minecraft.core.Direction::getName),
                  face.tintIndex(),
                  element.lightEmission()));
        }
      checkpoint.run();
      return new BarrelPreviewMesh(inputs, faces, !Boolean.FALSE.equals(model.ambientOcclusion()));
    }
  }
}
