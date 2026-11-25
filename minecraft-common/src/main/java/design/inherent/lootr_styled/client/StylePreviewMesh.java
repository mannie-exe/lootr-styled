package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.BarrelPreviewMesh;
import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.lootr_styled.resources.FrozenResources;
import design.inherent.lootr_styled.resources.TextureImages;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientGraphicImages;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLedger;
import design.inherent.reform.presentation.graphics.GraphicLighting;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMaterial;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import design.inherent.reform.presentation.graphics.GraphicScene;
import design.inherent.reform.presentation.graphics.GraphicTexture;
import design.inherent.reform.serialization.JsonLimits;
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

  /** CPU preparation only; the shared host owns this workflow ledger and later native lifetime. */
  GraphicScene prepare(GraphicLedger ledger, JsonLimits metadataLimits, Runnable checkpoint) {
    checkpoint.run();
    for (var entry : textures.entrySet())
      if (!entry.getKey().equals(entry.getValue().id()))
        throw new IllegalArgumentException("The captured texture identity changed");
    var ordered =
        textures.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(DocumentId::toString)))
            .map(Map.Entry::getValue)
            .toList();
    var images =
        ClientGraphicImages.decodeStills(
            ordered, ledger, metadataLimits, mesh.geometryBytes(), checkpoint);
    var scene = new GraphicScene(mesh, materials, images);
    scene.requireBudget(ledger.limits());
    checkpoint.run();
    return scene;
  }

  static StylePreviewMesh convert(
      BarrelPreviewMesh source,
      GraphicLimits limits,
      Function<DocumentId, GraphicMaterial> materialPolicy,
      Runnable checkpoint)
      throws IOException {
    var cached = new HashMap<DocumentId, GraphicMaterial>();
    return convert(
        source,
        limits,
        (face, texture) -> cached.computeIfAbsent(texture, materialPolicy),
        checkpoint);
  }

  static StylePreviewMesh convert(
      BarrelPreviewMesh source,
      GraphicLimits limits,
      java.util.function.BiFunction<BarrelPreviewMesh.Face, DocumentId, GraphicMaterial>
          materialPolicy,
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
    if (source.faces().stream().map(BarrelPreviewMesh.Face::texture).distinct().count()
        > limits.resources())
      throw new PolicyRefusal(
          "preview.budget", "The barrel exceeds the preview texture-count budget");
    var vertices = new ArrayList<GraphicMesh.Vertex>();
    var triangles = new ArrayList<GraphicMesh.Triangle>();
    var materials = new HashMap<DocumentId, GraphicMaterial>();
    var textures = new HashMap<DocumentId, GraphicTexture>();
    var materialIds = new HashMap<GraphicMaterial, DocumentId>();
    var sources = new HashMap<DocumentId, ResourceId>();
    for (var face : source.faces()) {
      checkpoint.run();
      if (face.tintIndex() != -1)
        throw new PolicyRefusal(
            "lootr_styled.preview.tint_unavailable",
            "The preview cannot determine this barrel's tint color. Choose another model.");
      var id =
          DocumentId.parse(
              "lootr_styled:preview/"
                  + TextureImages.hash(face.texture().toString().getBytes(StandardCharsets.UTF_8)));
      var material = java.util.Objects.requireNonNull(materialPolicy.apply(face, id));
      if (!material.texture().equals(id))
        throw new IllegalArgumentException("Preview material changed its captured texture binding");
      var materialId = materialIds.get(material);
      if (materialId == null) {
        materialId =
            !materials.containsKey(id)
                ? id
                : DocumentId.parse(
                    id
                        + "/material/"
                        + TextureImages.hash(
                            (material.alpha().name()
                                    + ":"
                                    + Float.floatToIntBits(material.cutoff())
                                    + ":"
                                    + material.cull().name()
                                    + ":"
                                    + material.filter().name()
                                    + ":"
                                    + material.wrap().name())
                                .getBytes(StandardCharsets.UTF_8)));
        materials.put(materialId, material);
        materialIds.put(material, materialId);
      }
      if (!textures.containsKey(id)) {
        textures.put(id, texture(source.inputs().resources(), face.texture(), id, limits));
        sources.put(id, face.texture());
      }
      if ((long) materials.size() + textures.size() > limits.resources())
        throw new PolicyRefusal(
            "preview.budget", "The barrel exceeds the complete material and texture allowance");
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
      triangles.add(new GraphicMesh.Triangle(first, first + 1, first + 2, materialId, lighting));
      triangles.add(new GraphicMesh.Triangle(first + 2, first + 3, first, materialId, lighting));
    }
    checkpoint.run();
    var mesh = new GraphicMesh(limits, vertices, triangles);
    return new StylePreviewMesh(mesh, materials, textures, sources);
  }

  static StylePreviewMesh chest(
      ChestPreviewInputs source,
      GraphicMesh mesh,
      GraphicLimits limits,
      Function<DocumentId, GraphicMaterial> materialPolicy,
      Runnable checkpoint)
      throws IOException {
    checkpoint.run();
    if (source.resources().byteSize() > limits.inputBytes()
        || mesh.geometryBytes() > limits.outputBytes())
      throw new PolicyRefusal(
          "preview.budget", "The chest exceeds its preview input or output budget");
    if (mesh.isEmpty()) return new StylePreviewMesh(mesh, Map.of(), Map.of(), Map.of());
    if (!mesh.materials().equals(java.util.Set.of(NativeChestPreview.MATERIAL)))
      throw new IllegalArgumentException("The chest mesh has unknown material bindings");
    var id = NativeChestPreview.MATERIAL;
    var material = java.util.Objects.requireNonNull(materialPolicy.apply(id));
    if (!material.texture().equals(id))
      throw new IllegalArgumentException(
          "Preview material changed its captured chest texture binding");
    var image = texture(source.resources(), source.texture(), id, limits);
    checkpoint.run();
    return new StylePreviewMesh(
        mesh, Map.of(id, material), Map.of(id, image), Map.of(id, source.texture()));
  }

  private static GraphicTexture texture(
      FrozenResources resources, ResourceId source, DocumentId id, GraphicLimits limits)
      throws IOException {
    byte[] png;
    try (var stream = resources.open(source).orElseThrow()) {
      png = stream.readAllBytes();
    }
    var companion = new ResourceId(source.namespace(), source.path() + ".mcmeta");
    Optional<byte[]> metadata = Optional.empty();
    if (resources.contains(companion))
      try (var stream = resources.open(companion).orElseThrow()) {
        metadata = Optional.of(stream.readAllBytes());
      }
    return new GraphicTexture(limits, id, png, metadata);
  }
}
