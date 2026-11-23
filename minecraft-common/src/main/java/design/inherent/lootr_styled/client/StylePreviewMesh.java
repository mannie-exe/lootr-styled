package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.BarrelPreviewMesh;
import design.inherent.lootr_styled.resources.TextureImages;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLighting;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMaterial;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import design.inherent.reform.presentation.graphics.GraphicTexture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Domain conversion only; shared graphics own native texture preparation and rendering. */
record StylePreviewMesh(
    GraphicMesh mesh,
    Map<DocumentId, GraphicMaterial> materials,
    Map<DocumentId, GraphicTexture> textures,
    Map<DocumentId, ResourceId> sources) {
  StylePreviewMesh {
    materials = Map.copyOf(materials);
    textures = Map.copyOf(textures);
    sources = Map.copyOf(sources);
  }

  static StylePreviewMesh convert(
      BarrelPreviewMesh source,
      GraphicLimits limits,
      Function<DocumentId, GraphicMaterial> materialPolicy,
      Runnable checkpoint)
      throws IOException {
    checkpoint.run();
    if (source.inputs().resources().byteSize() > limits.inputBytes())
      throw new PolicyRefusal(
          "preview.budget", "The captured barrel exceeds the preview input budget");
    long geometryBytes = (long) source.faces().size() * (4 * 36 + 2 * 16);
    if (geometryBytes > limits.outputBytes())
      throw new PolicyRefusal(
          "preview.budget", "The barrel mesh exceeds the preview output budget");
    var vertices = new ArrayList<GraphicMesh.Vertex>();
    var triangles = new ArrayList<GraphicMesh.Triangle>();
    var materials = new HashMap<DocumentId, GraphicMaterial>();
    var textures = new HashMap<DocumentId, GraphicTexture>();
    var sources = new HashMap<DocumentId, ResourceId>();
    for (var face : source.faces()) {
      checkpoint.run();
      if (face.tintIndex() != -1)
        throw new PolicyRefusal(
            "lootr_styled.preview.tint_unavailable",
            "This barrel needs a native tint provider; its color cannot be inferred from the profile");
      var id =
          DocumentId.parse(
              "lootr_styled:preview/"
                  + TextureImages.hash(face.texture().toString().getBytes(StandardCharsets.UTF_8)));
      if (!textures.containsKey(id)) {
        byte[] png;
        try (var stream = source.inputs().resources().open(face.texture()).orElseThrow()) {
          png = stream.readAllBytes();
        }
        var companion =
            new ResourceId(face.texture().namespace(), face.texture().path() + ".mcmeta");
        Optional<byte[]> metadata = Optional.empty();
        if (source.inputs().resources().contains(companion))
          try (var stream = source.inputs().resources().open(companion).orElseThrow()) {
            metadata = Optional.of(stream.readAllBytes());
          }
        var material = java.util.Objects.requireNonNull(materialPolicy.apply(id));
        if (!material.texture().equals(id))
          throw new IllegalArgumentException(
              "Preview material changed its captured texture binding");
        materials.put(id, material);
        textures.put(id, new GraphicTexture(limits, id, png, metadata));
        sources.put(id, face.texture());
      }
      var normal = GraphicLighting.Direction.valueOf(face.direction().toUpperCase(Locale.ROOT));
      float nx =
          normal == GraphicLighting.Direction.EAST
              ? 1
              : normal == GraphicLighting.Direction.WEST ? -1 : 0;
      float ny =
          normal == GraphicLighting.Direction.UP
              ? 1
              : normal == GraphicLighting.Direction.DOWN ? -1 : 0;
      float nz =
          normal == GraphicLighting.Direction.SOUTH
              ? 1
              : normal == GraphicLighting.Direction.NORTH ? -1 : 0;
      int first = vertices.size();
      for (var vertex : face.vertices())
        vertices.add(
            new GraphicMesh.Vertex(
                vertex.x(),
                vertex.y(),
                vertex.z(),
                vertex.u(),
                vertex.v(),
                0xffffffff,
                nx,
                ny,
                nz));
      var lighting =
          new GraphicLighting(
              face.shade() ? GraphicLighting.Shading.DIRECTIONAL : GraphicLighting.Shading.UNLIT,
              face.shadeDirectionOverride()
                  .map(value -> GraphicLighting.Direction.valueOf(value.toUpperCase(Locale.ROOT))),
              face.lightEmission(),
              source.ambientOcclusion());
      triangles.add(new GraphicMesh.Triangle(first, first + 1, first + 2, id, lighting));
      triangles.add(new GraphicMesh.Triangle(first + 2, first + 3, first, id, lighting));
    }
    checkpoint.run();
    var mesh = new GraphicMesh(limits, vertices, triangles);
    return new StylePreviewMesh(mesh, materials, textures, sources);
  }
}
