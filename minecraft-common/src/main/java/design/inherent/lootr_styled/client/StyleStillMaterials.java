package design.inherent.lootr_styled.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMaterial;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import design.inherent.reform.presentation.graphics.GraphicPixels;
import design.inherent.reform.presentation.graphics.GraphicScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Isolated nearest/clamp stills, independent of the live atlas and world render layers. */
final class StyleStillMaterials {
  private static final int TILE = 16;

  private StyleStillMaterials() {}

  static GraphicMaterial material(DocumentId texture, GraphicMaterial.Alpha alpha) {
    return new GraphicMaterial(
        texture,
        alpha,
        alpha == GraphicMaterial.Alpha.CUTOUT ? 0.1f : 0,
        GraphicMaterial.Cull.BACK,
        GraphicMaterial.Filter.NEAREST,
        GraphicMaterial.Wrap.CLAMP);
  }

  static GraphicScene classify(GraphicScene source, GraphicLimits limits, Runnable checkpoint) {
    source.requireBudget(limits);
    var work = new Work(limits.decodedPixels(), checkpoint);
    var indexes = new HashMap<DocumentId, AlphaIndex>();
    var triangles = new ArrayList<GraphicMesh.Triangle>();
    var materials = new HashMap<DocumentId, GraphicMaterial>();
    // One small tile index per texture, bounded independently of the retained scene output.
    long scratch = 0;
    for (var entry : source.textures().entrySet()) {
      checkpoint.run();
      var image = entry.getValue();
      long entries =
          (long) ((image.width() + TILE - 1) / TILE + 1) * ((image.height() + TILE - 1) / TILE + 1);
      long bytes = entries * Integer.BYTES * 2;
      if (bytes > limits.outputBytes() - scratch || entries > Integer.MAX_VALUE)
        throw new PolicyRefusal(
            "preview.budget", "The still transparency index exceeds its working allowance");
      scratch += bytes;
      indexes.put(entry.getKey(), new AlphaIndex(image, work));
    }
    for (var triangle : source.mesh().triangles()) {
      checkpoint.run();
      var texture = source.materials().get(triangle.material()).texture();
      var vertices = source.mesh().vertices();
      var a = vertices.get(triangle.a());
      var b = vertices.get(triangle.b());
      var c = vertices.get(triangle.c());
      // Domain meshes come from native cuboid quads. Each triangle spans its quad's rectangular
      // UV footprint, so both halves receive the same material without changing their winding.
      var alpha =
          indexes
              .get(texture)
              .classify(
                  Math.min(a.u(), Math.min(b.u(), c.u())),
                  Math.min(a.v(), Math.min(b.v(), c.v())),
                  Math.max(a.u(), Math.max(b.u(), c.u())),
                  Math.max(a.v(), Math.max(b.v(), c.v())));
      var id =
          DocumentId.parse(texture + "/still/" + alpha.name().toLowerCase(java.util.Locale.ROOT));
      materials.putIfAbsent(id, material(texture, alpha));
      triangles.add(
          new GraphicMesh.Triangle(
              triangle.a(), triangle.b(), triangle.c(), id, triangle.lighting()));
    }
    var mesh = new GraphicMesh(limits, source.mesh().vertices(), triangles);
    var result = new GraphicScene(mesh, materials, source.textures());
    result.requireBudget(limits);
    checkpoint.run();
    return result;
  }

  /** Two tile-level summed-area tables; boundary texels are inspected exactly. */
  private static final class AlphaIndex {
    private final GraphicPixels image;
    private final Work work;
    private final int stride;
    private final int[] zeros, partials;
    private final Map<Rect, GraphicMaterial.Alpha> cached = new HashMap<>();
    private final boolean opaque;

    AlphaIndex(GraphicPixels image, Work work) {
      this.image = image;
      this.work = work;
      int columns = (image.width() + TILE - 1) / TILE;
      int rows = (image.height() + TILE - 1) / TILE;
      stride = columns + 1;
      zeros = new int[(rows + 1) * stride];
      partials = new int[zeros.length];
      for (int y = 0; y < image.height(); y++) {
        work.checkpoint.run();
        for (int x = 0; x < image.width(); x++) {
          work.visit();
          int alpha = image.pixel(x, y) >>> 24;
          int index = (y / TILE + 1) * stride + x / TILE + 1;
          if (alpha == 0) zeros[index] = 1;
          else if (alpha != 255) partials[index] = 1;
        }
      }
      for (int y = 1; y <= rows; y++) {
        work.checkpoint.run();
        for (int x = 1; x <= columns; x++) {
          int i = y * stride + x;
          zeros[i] += zeros[i - 1] + zeros[i - stride] - zeros[i - stride - 1];
          partials[i] += partials[i - 1] + partials[i - stride] - partials[i - stride - 1];
        }
      }
      opaque = zeros[zeros.length - 1] == 0 && partials[partials.length - 1] == 0;
    }

    GraphicMaterial.Alpha classify(float u0, float v0, float u1, float v1) {
      if (opaque) return GraphicMaterial.Alpha.OPAQUE;
      // Half-open nondegenerate bounds exclude a neighboring atlas cell at the upper edge.
      // Degenerate coordinates select the clamped nearest texel, including u/v == 1.
      int x0 = lower(u0, image.width()), y0 = lower(v0, image.height());
      int x1 = upper(u1, image.width(), x0), y1 = upper(v1, image.height(), y0);
      return cached.computeIfAbsent(new Rect(x0, y0, x1, y1), this::scan);
    }

    private GraphicMaterial.Alpha scan(Rect r) {
      int left = (r.x0 + TILE - 1) / TILE, top = (r.y0 + TILE - 1) / TILE;
      int right = r.x1 / TILE, bottom = r.y1 / TILE;
      boolean wholeTiles = left < right && top < bottom;
      boolean zero = wholeTiles && sum(zeros, left, top, right, bottom) > 0;
      if (wholeTiles && sum(partials, left, top, right, bottom) > 0)
        return GraphicMaterial.Alpha.TRANSLUCENT;
      for (int y = r.y0; y < r.y1; y++) {
        work.checkpoint.run();
        for (int x = r.x0; x < r.x1; x++) {
          if (wholeTiles
              && x >= left * TILE
              && x < right * TILE
              && y >= top * TILE
              && y < bottom * TILE) {
            x = right * TILE - 1;
            continue;
          }
          work.visit();
          int alpha = image.pixel(x, y) >>> 24;
          if (alpha > 0 && alpha < 255) return GraphicMaterial.Alpha.TRANSLUCENT;
          zero |= alpha == 0;
        }
      }
      return zero ? GraphicMaterial.Alpha.CUTOUT : GraphicMaterial.Alpha.OPAQUE;
    }

    private int sum(int[] values, int x0, int y0, int x1, int y1) {
      return values[y1 * stride + x1]
          - values[y1 * stride + x0]
          - values[y0 * stride + x1]
          + values[y0 * stride + x0];
    }

    private static int lower(float value, int size) {
      return (int) Math.max(0, Math.min(size - 1, Math.floor((double) value * size)));
    }

    private static int upper(float value, int size, int lower) {
      return (int) Math.max(lower + 1, Math.min(size, Math.ceil((double) value * size)));
    }
  }

  private record Rect(int x0, int y0, int x1, int y1) {}

  private static final class Work {
    private long remaining;
    private int sinceCheck;
    private final Runnable checkpoint;

    Work(long pixels, Runnable checkpoint) {
      remaining = pixels > Long.MAX_VALUE / 8 ? Long.MAX_VALUE : pixels * 8;
      this.checkpoint = checkpoint;
    }

    void visit() {
      if (remaining-- == 0)
        throw new PolicyRefusal(
            "preview.budget", "The still transparency check exceeded its sampling allowance");
      if (++sinceCheck == 4096) {
        sinceCheck = 0;
        checkpoint.run();
      }
    }
  }
}
