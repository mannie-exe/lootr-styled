package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test-only sampling of actual native baked vertices and generated mip images. */
public final class BarrelSampling {
  public static final int ATLAS_WIDTH = 8192, ATLAS_HEIGHT = 4096;

  private BarrelSampling() {}

  public static byte[] resource(ResourceId id) throws Exception {
    String path = "assets/" + id.namespace() + "/" + id.path();
    String root = System.getProperty("barrel.fixtureRoot");
    if (root != null) return Files.readAllBytes(Path.of(root).resolve(path));
    try (var in = BarrelSampling.class.getResourceAsStream("/" + path)) {
      assertNotNull(in, path);
      return in.readAllBytes();
    }
  }

  public static JsonObject model(boolean looted, boolean open, boolean packed) throws Exception {
    var target =
        ContainerTargets.LEGACY.barrels().stream()
            .filter(
                b -> b.open() == open && b.material().equals(looted ? "opened_barrel" : "barrel"))
            .findFirst()
            .orElseThrow();
    var id = BarrelCompiler.defaultModel(target, packed);
    if (System.getProperty("barrel.fixtureRoot") != null
        && !Boolean.getBoolean("barrel.fixtureBundled")) {
      id =
          packed
              ? target.target()
              : ResourceId.parse(
                  "lootr_styled:models/block/native/"
                      + target.target().path().substring("models/block/".length()));
    }
    return JsonDocuments.object(resource(id));
  }

  public static ResourceId image(JsonObject model, String slot, boolean identicalNativeTops) {
    String name = slot;
    while (name.startsWith("#"))
      name = model.getAsJsonObject("textures").get(name.substring(1)).getAsString();
    var id = ResourceId.parse(name);
    if (identicalNativeTops && id.namespace().equals("lootr") && id.path().endsWith("_top_open"))
      id = ResourceId.parse("lootr:" + id.path().replace("_top_open", "_top"));
    if ((System.getProperty("barrel.fixtureRoot") == null
            || Boolean.getBoolean("barrel.fixtureBundled"))
        && id.namespace().equals("lootr"))
      return ResourceId.parse("lootr_styled:textures/default/" + id.path() + ".png");
    return ResourceId.parse(id.namespace() + ":textures/" + id.path() + ".png");
  }

  public interface Pixels {
    int color(int level, int x, int y);

    int width(int level);

    int height(int level);

    default boolean visible(int color) {
      return color >>> 24 == 255;
    }
  }

  public record Quad(String direction, float[][] positions, float[][] uv, Pixels pixels) {
    private int axis() {
      return direction.equals("up") || direction.equals("down")
          ? 1
          : direction.equals("east") || direction.equals("west") ? 0 : 2;
    }

    private int sign() {
      return List.of("up", "east", "south").contains(direction) ? 1 : -1;
    }

    private boolean covers(float[] point) {
      for (int k = 0; k < 3; k++) {
        if (k == axis()) continue;
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (float[] vertex : positions) {
          min = Math.min(min, vertex[k]);
          max = Math.max(max, vertex[k]);
        }
        if (point[k] < min || point[k] > max) return false;
      }
      return true;
    }

    private int sample(float[] p, int level) {
      float a = 0, b = 0;
      for (int k = 0; k < 3; k++) {
        float eA = positions[3][k] - positions[0][k], eB = positions[1][k] - positions[0][k];
        if (eA != 0) a = (p[k] - positions[0][k]) / eA;
        if (eB != 0) b = (p[k] - positions[0][k]) / eB;
      }
      float u = uv[0][0] + a * (uv[3][0] - uv[0][0]) + b * (uv[1][0] - uv[0][0]);
      float v = uv[0][1] + a * (uv[3][1] - uv[0][1]) + b * (uv[1][1] - uv[0][1]);
      int x =
          Math.clamp((int) Math.floor(u * ATLAS_WIDTH / (1 << level)), 0, pixels.width(level) - 1);
      int y =
          Math.clamp(
              (int) Math.floor(v * ATLAS_HEIGHT / (1 << level)), 0, pixels.height(level) - 1);
      return pixels.color(level, x, y);
    }
  }

  public static int sample(List<Quad> quads, String face, float[] point, int level) {
    Quad best = null;
    for (var q : quads)
      if (q.direction.equals(face) && q.covers(point)) {
        if (best == null || q.sign() * (q.positions[0][q.axis()] - best.positions[0][q.axis()]) > 0)
          best = q;
      }
    assertNotNull(best, "Missing native face at " + face + java.util.Arrays.toString(point));
    return best.sample(point, level);
  }

  /** Centers and both sides of every texel boundary, including persistent raised rim cells. */
  public static int differences(List<Quad> closed, List<Quad> open, int level, boolean rim) {
    int changed = 0;
    float[] fractions = {.01f, .5f, .99f};
    for (String face : List.of("north", "south", "west", "east"))
      for (int y = 0; y < 16; y++)
        for (int t = 0; t < 16; t++)
          for (float fy : fractions)
            for (float ft : fractions) {
              float h = (y + fy) / 16, tangent = (t + ft) / 16;
              float[] p =
                  face.equals("east") || face.equals("west")
                      ? new float[] {0, h, tangent}
                      : new float[] {tangent, h, 0};
              int a = sample(closed, face, p, level), b = sample(open, face, p, level);
              assertTrue(
                  closed.stream()
                      .filter(q -> q.direction.equals(face))
                      .findFirst()
                      .orElseThrow()
                      .pixels
                      .visible(a),
                  "Opaque barrel lost native alpha coverage");
              assertTrue(
                  open.stream()
                      .filter(q -> q.direction.equals(face))
                      .findFirst()
                      .orElseThrow()
                      .pixels
                      .visible(b),
                  "Opaque barrel lost native alpha coverage");
              if (a != b) changed++;
            }
    if (rim) {
      try (var in = BarrelSampling.class.getResourceAsStream("/fixtures/barrels/blueprint.json")) {
        var states = JsonDocuments.object(in.readAllBytes()).getAsJsonObject("states");
        var c = states.getAsJsonObject("closed").getAsJsonArray("layers").get(15).getAsJsonArray();
        var o = states.getAsJsonObject("open").getAsJsonArray("layers").get(15).getAsJsonArray();
        int cells = 0;
        for (int z = 0; z < 16; z++)
          for (int x = 0; x < 16; x++)
            if (c.get(z).getAsString().charAt(x) == 'T'
                && o.get(z).getAsString().charAt(x) == 'T') {
              cells++;
              for (float fx : fractions)
                for (float fz : fractions) {
                  float[] p = {(x + fx) / 16, 1, (z + fz) / 16};
                  if (sample(closed, "up", p, level) != sample(open, "up", p, level)) changed++;
                }
            }
        assertEquals(68, cells);
      } catch (Exception ex) {
        throw new AssertionError(ex);
      }
    }
    return changed;
  }
}
