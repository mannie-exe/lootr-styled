package design.inherent.lootr_styled.client;

import design.inherent.reform.presentation.graphics.GraphicLighting;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import design.inherent.reform.presentation.graphics.GraphicScene;
import java.util.ArrayList;

/** Explicit isolated reference lighting; source/world lighting remains separate inspection data. */
final class StyleReferenceLighting {
  record Result(GraphicScene scene, int nativeAoTriangles) {}

  private StyleReferenceLighting() {}

  static Result prepare(GraphicScene source, GraphicLimits limits, Runnable checkpoint) {
    checkpoint.run();
    source.requireBudget(limits);
    int count = 0;
    for (var triangle : source.mesh().triangles()) {
      checkpoint.run();
      if (triangle.lighting().ambientOcclusion()) count++;
    }
    if (count == 0) return new Result(source, 0);
    var triangles = new ArrayList<GraphicMesh.Triangle>(source.mesh().triangles().size());
    for (var triangle : source.mesh().triangles()) {
      checkpoint.run();
      var light = triangle.lighting();
      triangles.add(
          light.ambientOcclusion()
              ? new GraphicMesh.Triangle(
                  triangle.a(),
                  triangle.b(),
                  triangle.c(),
                  triangle.material(),
                  new GraphicLighting(
                      light.shading(), light.direction(), light.lightEmission(), false))
              : triangle);
    }
    var mesh = new GraphicMesh(limits, source.mesh().vertices(), triangles);
    checkpoint.run();
    return new Result(new GraphicScene(mesh, source.materials(), source.textures()), count);
  }
}
