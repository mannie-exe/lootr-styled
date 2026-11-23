package design.inherent.lootr_styled.client;

import com.mojang.blaze3d.platform.NativeImage;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.BarrelPreviewInputs;
import design.inherent.lootr_styled.resources.BarrelPreviewMesh;
import design.inherent.lootr_styled.resources.JsonDocuments;
import design.inherent.lootr_styled.resources.TextureImages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Native CPU baking only. Shared re:form graphics own private GPU upload and submission. */
final class NativeBarrelPreview {
  private NativeBarrelPreview() {}

  private static final class Sprite extends TextureAtlasSprite {
    Sprite(SpriteContents contents) {
      super(
          ResourceLocation.parse("lootr_styled:isolated_preview"),
          contents,
          contents.width(),
          contents.height(),
          0,
          0);
    }
  }

  private static final class Images implements AutoCloseable {
    private final BarrelPreviewInputs inputs;
    private final Map<String, SpriteContents> owned = new HashMap<>();
    private final Map<ResourceId, Sprite> sprites = new HashMap<>();
    private long decodedPixels;

    Images(BarrelPreviewInputs inputs) {
      this.inputs = inputs;
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
              AnimationMetadataSection.SERIALIZER
                  .fromJson(json.getAsJsonObject("animation"))
                  .calculateFrameSize(size.width(), size.height());
      }
      String key = TextureImages.hash(bytes) + ":" + frame.width() + ":" + frame.height();
      var contents = owned.get(key);
      if (contents == null) {
        long area = (long) size.width() * size.height();
        if (area > TextureImages.MAX_PIXELS - decodedPixels)
          throw new design.inherent.lootr_styled.resources.ResourceInputs.LimitExceeded(
              "Isolated native texture pixel budget exceeded");
        NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
        try {
          if (image.getWidth() != size.width() || image.getHeight() != size.height())
            throw new IllegalArgumentException("Native preview image dimensions changed");
          contents =
              new SpriteContents(
                  ResourceLocation.parse(id.toString()), frame, image, ResourceMetadata.EMPTY);
          owned.put(key, contents);
          decodedPixels += area;
        } catch (RuntimeException failure) {
          image.close();
          throw failure;
        }
      }
      var sprite = new Sprite(contents);
      sprites.put(id, sprite);
      return sprite;
    }

    @Override
    public void close() {
      RuntimeException failed = null;
      for (var contents : owned.values()) {
        try {
          contents.close();
        } catch (RuntimeException error) {
          if (failed == null) failed = error;
          else failed.addSuppressed(error);
        }
      }
      sprites.clear();
      owned.clear();
      if (failed != null) throw failed;
    }
  }

  @SuppressWarnings("deprecation")
  static BarrelPreviewMesh bake(BarrelPreviewInputs inputs, Runnable checkpoint)
      throws IOException {
    checkpoint.run();
    byte[] bytes;
    try (var stream = inputs.resources().open(inputs.barrel().target()).orElseThrow()) {
      bytes = stream.readAllBytes();
    }
    var json = JsonDocuments.object(bytes);
    var model = BlockModel.fromString(new String(bytes, StandardCharsets.UTF_8));
    var faces = new ArrayList<BarrelPreviewMesh.Face>();
    var baker = new FaceBakery();
    try (var images = new Images(inputs)) {
      for (var element : model.getElements())
        for (var entry : element.faces.entrySet()) {
          checkpoint.run();
          var texture = BarrelPreviewMesh.texture(json, entry.getValue().texture());
          var quad =
              baker.bakeQuad(
                  element.from,
                  element.to,
                  entry.getValue(),
                  images.sprite(texture),
                  entry.getKey(),
                  BlockModelRotation.X0_Y0,
                  element.rotation,
                  element.shade);
          var data = quad.getVertices();
          var vertices = new ArrayList<BarrelPreviewMesh.Vertex>();
          for (int vertex = 0; vertex < 4; vertex++)
            vertices.add(
                new BarrelPreviewMesh.Vertex(
                    Float.intBitsToFloat(data[vertex * 8]),
                    Float.intBitsToFloat(data[vertex * 8 + 1]),
                    Float.intBitsToFloat(data[vertex * 8 + 2]),
                    Float.intBitsToFloat(data[vertex * 8 + 4]),
                    Float.intBitsToFloat(data[vertex * 8 + 5])));
          faces.add(
              new BarrelPreviewMesh.Face(
                  texture,
                  List.copyOf(vertices),
                  quad.getDirection().getName(),
                  quad.isShade(),
                  Optional.empty(),
                  quad.getTintIndex(),
                  0));
        }
      checkpoint.run();
      return new BarrelPreviewMesh(inputs, faces, model.hasAmbientOcclusion());
    }
  }
}
