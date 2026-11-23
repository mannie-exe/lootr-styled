package design.inherent.lootr_styled.resources;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import java.util.List;
import java.util.Optional;

/** Copied native face data for an isolated barrel; contains no sprite or native resource handle. */
public record BarrelPreviewMesh(
    BarrelPreviewInputs inputs, List<Face> faces, boolean ambientOcclusion) {
  public BarrelPreviewMesh {
    java.util.Objects.requireNonNull(inputs);
    faces = List.copyOf(faces);
    if (faces.size() > 6 * 1024) throw new IllegalArgumentException("Preview face budget exceeded");
  }

  public record Vertex(float x, float y, float z, float u, float v) {
    public Vertex {
      if (!Float.isFinite(x)
          || !Float.isFinite(y)
          || !Float.isFinite(z)
          || !Float.isFinite(u)
          || !Float.isFinite(v)) throw new IllegalArgumentException("Nonfinite preview vertex");
    }
  }

  public record Face(
      ResourceId texture,
      List<Vertex> vertices,
      String direction,
      boolean shade,
      Optional<String> shadeDirectionOverride,
      int tintIndex,
      int lightEmission) {
    public Face {
      java.util.Objects.requireNonNull(texture);
      vertices = List.copyOf(vertices);
      if (vertices.size() != 4)
        throw new IllegalArgumentException("Native preview face needs four vertices");
      java.util.Objects.requireNonNull(direction);
      java.util.Objects.requireNonNull(shadeDirectionOverride);
    }
  }

  public static ResourceId texture(JsonObject model, String reference) {
    var textures = model.getAsJsonObject("textures");
    var visited = new java.util.HashSet<String>();
    while (reference.startsWith("#")) {
      String name = reference.substring(1);
      if (!visited.add(name) || textures == null || !textures.has(name))
        throw new IllegalArgumentException("Missing or cyclic preview texture " + reference);
      reference = textures.get(name).getAsString();
    }
    var name = BlockModels.resourceName(reference);
    return new ResourceId(name.namespace(), "textures/" + name.path() + ".png");
  }
}
