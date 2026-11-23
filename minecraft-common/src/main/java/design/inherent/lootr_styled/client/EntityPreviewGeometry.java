package design.inherent.lootr_styled.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLighting;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import java.util.ArrayList;
import net.minecraft.client.model.geom.ModelPart;

/** Native cube emission from an owned detached model, without invoking its renderer/animations. */
final class EntityPreviewGeometry {
  private EntityPreviewGeometry() {}

  static GraphicMesh capture(
      ModelPart owned, DocumentId material, GraphicLimits limits, Runnable checkpoint) {
    var sink = new Sink(material, limits, checkpoint);
    owned.visit(
        new PoseStack(),
        (pose, path, index, cube) -> {
          checkpoint.run();
          if (!draws(owned, path)) return;
          int before = sink.vertices.size();
          cube.compile(pose, sink, 0, 0, 0xffffffff);
          if ((sink.vertices.size() - before) % 4 != 0)
            throw new PolicyRefusal(
                "lootr_styled.preview.vertex_format",
                "The native chest geometry did not emit complete quadrilateral faces");
        });
    return new GraphicMesh(limits, sink.vertices, sink.triangles);
  }

  /**
   * Count visible geometry under the exact native attachment; animation expressions are not run.
   */
  static boolean hasVisibleGeometry(ModelPart owned, Runnable checkpoint) {
    boolean[] found = {false};
    owned.visit(
        new PoseStack(),
        (pose, path, index, cube) -> {
          checkpoint.run();
          if (draws(owned, path)) found[0] = true;
        });
    checkpoint.run();
    return found[0];
  }

  private static boolean draws(ModelPart root, String path) {
    var part = root;
    if (!part.visible) return false;
    for (String name : path.split("/")) {
      if (name.isEmpty()) continue;
      part = part.getChild(name);
      if (!part.visible) return false;
    }
    return !part.skipDraw;
  }

  private static final class Sink implements VertexConsumer {
    private final ArrayList<GraphicMesh.Vertex> vertices = new ArrayList<>();
    private final ArrayList<GraphicMesh.Triangle> triangles = new ArrayList<>();
    private final DocumentId material;
    private final GraphicLimits limits;
    private final Runnable checkpoint;

    Sink(DocumentId material, GraphicLimits limits, Runnable checkpoint) {
      this.material = material;
      this.limits = limits;
      this.checkpoint = checkpoint;
    }

    @Override
    public void addVertex(
        float x,
        float y,
        float z,
        int argb,
        float u,
        float v,
        int overlay,
        int light,
        float nx,
        float ny,
        float nz) {
      checkpoint.run();
      long bytes = (vertices.size() + 1L) * 36 + ((vertices.size() + 1L) / 4) * 32;
      if (bytes > limits.outputBytes())
        throw new PolicyRefusal("preview.budget", "The chest mesh exceeds its output-byte budget");
      vertices.add(new GraphicMesh.Vertex(x, y, z, u, v, argb, nx, ny, nz));
      if (vertices.size() % 4 == 0) {
        int first = vertices.size() - 4;
        triangles.add(
            new GraphicMesh.Triangle(
                first, first + 1, first + 2, material, GraphicLighting.DIRECTIONAL));
        triangles.add(
            new GraphicMesh.Triangle(
                first + 2, first + 3, first, material, GraphicLighting.DIRECTIONAL));
      }
    }

    private static PolicyRefusal changed() {
      return new PolicyRefusal(
          "lootr_styled.preview.vertex_format",
          "The native chest vertex format is unsupported; its preview was not prepared");
    }

    // Native ModelPart.Cube uses the complete vertex overload in both supported games. Refuse
    // a changed producer rather than invent attributes. The last two methods exist only in26.3.
    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      throw changed();
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      throw changed();
    }

    @Override
    public VertexConsumer setColor(int color) {
      throw changed();
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      throw changed();
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      throw changed();
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      throw changed();
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      throw changed();
    }

    public VertexConsumer setUv3(float u, float v) {
      throw changed();
    }

    public VertexConsumer setLineWidth(float width) {
      throw changed();
    }
  }
}
