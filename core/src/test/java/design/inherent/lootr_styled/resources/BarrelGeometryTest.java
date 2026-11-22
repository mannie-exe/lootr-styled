package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Compare compiled models with the owner's independent voxel reference, not the mesher. */
class BarrelGeometryTest {
  private static JsonObject json(String name) throws Exception {
    try (var input = BarrelGeometryTest.class.getResourceAsStream(name)) {
      assertNotNull(input, name);
      return JsonDocuments.object(input.readAllBytes());
    }
  }

  private static JsonObject model(boolean looted, boolean open, boolean packed) throws Exception {
    var target =
        ContainerTargets.LEGACY.barrels().stream()
            .filter(
                b -> b.open() == open && b.material().equals(looted ? "opened_barrel" : "barrel"))
            .findFirst()
            .orElseThrow();
    ResourceId id = BarrelCompiler.defaultModel(target, packed);
    return json("/assets/" + id.namespace() + "/" + id.path());
  }

  record Cell(int x, int y, int z) {
    Cell move(int x, int y, int z) {
      return new Cell(this.x + x, this.y + y, this.z + z);
    }
  }

  private static Set<Cell> reference(boolean open) throws Exception {
    var state =
        json("/fixtures/barrels/blueprint.json")
            .getAsJsonObject("states")
            .getAsJsonObject(open ? "open" : "closed");
    var layers = state.getAsJsonArray("layers");
    Set<Cell> cells = new HashSet<>();
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++) {
        String row = layers.get(y).getAsJsonArray().get(z).getAsString();
        for (int x = 0; x < 16; x++) if (row.charAt(x) != '.') cells.add(new Cell(x, y, z));
      }
    if (state.get("fill_enclosed").getAsBoolean()) {
      Set<Cell> exterior = new HashSet<>();
      var pending = new ArrayDeque<Cell>();
      pending.add(new Cell(-1, -1, -1));
      while (!pending.isEmpty()) {
        Cell c = pending.removeFirst();
        if (c.x < -1
            || c.y < -1
            || c.z < -1
            || c.x > 16
            || c.y > 16
            || c.z > 16
            || cells.contains(c)
            || !exterior.add(c)) continue;
        pending.add(c.move(1, 0, 0));
        pending.add(c.move(-1, 0, 0));
        pending.add(c.move(0, 1, 0));
        pending.add(c.move(0, -1, 0));
        pending.add(c.move(0, 0, 1));
        pending.add(c.move(0, 0, -1));
      }
      for (int x = 0; x < 16; x++)
        for (int y = 0; y < 16; y++)
          for (int z = 0; z < 16; z++) {
            var c = new Cell(x, y, z);
            if (!exterior.contains(c)) cells.add(c);
          }
    }
    return cells;
  }

  private static int[] point(JsonObject e, String key) {
    var a = e.getAsJsonArray(key);
    for (var coordinate : a)
      assertEquals(
          Math.rint(coordinate.getAsDouble()),
          coordinate.getAsDouble(),
          "Default must retain integer owner voxels");
    return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
  }

  private static Set<Cell> occupied(JsonObject model) {
    Set<Cell> cells = new HashSet<>();
    for (var value : model.getAsJsonArray("elements")) {
      var e = value.getAsJsonObject();
      int[] a = point(e, "from"), b = point(e, "to");
      for (int x = a[0]; x < b[0]; x++)
        for (int y = a[1]; y < b[1]; y++)
          for (int z = a[2]; z < b[2]; z++)
            assertTrue(cells.add(new Cell(x, y, z)), "Overlapping cuboid volume");
    }
    return cells;
  }

  @Test
  void allMaterialAndUvVariantsPreserveExactReferenceVolume() throws Exception {
    for (boolean open : List.of(false, true)) {
      var expected = reference(open);
      assertEquals(open ? 1272 : 3912, expected.size());
      for (boolean looted : List.of(false, true))
        for (boolean packed : List.of(false, true))
          assertEquals(expected, occupied(model(looted, open, packed)));
    }
  }

  @Test
  void opaqueWallsFloorAndClosedBoundaryRimSealNativeNeighborContacts() throws Exception {
    for (boolean open : List.of(false, true)) {
      var cells = occupied(model(false, open, true));
      for (int a = 0; a < 16; a++)
        for (int b = 0; b < 16; b++) {
          assertTrue(cells.contains(new Cell(a, 0, b)), "Floor must seal its neighbor");
          assertTrue(cells.contains(new Cell(0, a, b)), "West wall must seal its neighbor");
          assertTrue(cells.contains(new Cell(15, a, b)), "East wall must seal its neighbor");
          assertTrue(cells.contains(new Cell(a, b, 0)), "North wall must seal its neighbor");
          assertTrue(cells.contains(new Cell(a, b, 15)), "South wall must seal its neighbor");
        }
    }
  }

  @Test
  void onlyTheClosedCarrierHasAFullyCoveredFace() throws Exception {
    for (boolean open : List.of(false, true)) {
      var model = model(false, open, true);
      var cells = occupied(model);
      int covered = 0;
      for (var value : model.getAsJsonArray("elements")) {
        var e = value.getAsJsonObject();
        int[] a = point(e, "from"), b = point(e, "to");
        for (var face : e.getAsJsonObject("faces").entrySet()) {
          String d = face.getKey();
          int axis =
              d.equals("up") || d.equals("down") ? 1 : d.equals("east") || d.equals("west") ? 0 : 2;
          boolean positive = List.of("up", "south", "east").contains(d);
          int plane = positive ? b[axis] : a[axis];
          int first = (axis + 1) % 3, second = (axis + 2) % 3;
          boolean hidden = true;
          for (int i = a[first]; i < b[first]; i++)
            for (int j = a[second]; j < b[second]; j++) {
              int[] neighbor = new int[3];
              neighbor[axis] = positive ? plane : plane - 1;
              neighbor[first] = i;
              neighbor[second] = j;
              hidden &= cells.contains(new Cell(neighbor[0], neighbor[1], neighbor[2]));
            }
          if (hidden) {
            covered++;
            assertEquals("down", d);
            assertEquals(1, plane);
          }
          if (plane != 0 && plane != 16)
            assertFalse(
                face.getValue().getAsJsonObject().has("cullface"),
                "Inset face cannot use boundary culling");
        }
      }
      assertEquals(open ? 0 : 1, covered);
    }
  }

  private static Set<String> exterior(JsonObject model, boolean uv) {
    Set<String> result = new HashSet<>();
    for (var value : model.getAsJsonArray("elements")) {
      var e = value.getAsJsonObject();
      int[] a = point(e, "from"), b = point(e, "to");
      for (String d : List.of("north", "south", "west", "east")) {
        var face = e.getAsJsonObject("faces").get(d);
        if (face == null) continue;
        int axis = (d.equals("north") || d.equals("south")) ? 2 : 0;
        boolean positive = d.equals("south") || d.equals("east");
        int plane = positive ? b[axis] : a[axis];
        if (plane != (positive ? 16 : 0)) continue;
        int tangent = axis == 2 ? 0 : 2;
        String key = d + ":" + a[tangent] + "," + a[1] + "," + b[tangent] + "," + b[1];
        if (uv) {
          var f = face.getAsJsonObject();
          String texture = f.get("texture").getAsString();
          while (texture.startsWith("#"))
            texture = model.getAsJsonObject("textures").get(texture.substring(1)).getAsString();
          key += ":" + f.get("uv") + ":" + f.get("rotation") + ":" + texture;
        }
        assertTrue(result.add(key), "Overlapping exterior faces");
      }
    }
    return result;
  }

  @Test
  void persistentWallsUseTheSameQuadsAndUvBindingsAcrossOpening() throws Exception {
    for (boolean looted : List.of(false, true))
      for (boolean packed : List.of(false, true)) {
        var closed = model(looted, false, packed);
        var open = model(looted, true, packed);
        var surfaces = exterior(closed, true);
        assertFalse(surfaces.isEmpty());
        assertEquals(surfaces, exterior(open, true));
      }
  }

  @Test
  void nativeAndPackedVariantsHaveIdenticalExteriorGeometry() throws Exception {
    for (boolean looted : List.of(false, true))
      for (boolean open : List.of(false, true))
        assertEquals(
            exterior(model(looted, open, false), false),
            exterior(model(looted, open, true), false));
  }
}
