package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.StylePreviewCapture;
import design.inherent.lootr_styled.profile.LocalFiles;
import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.lootr_styled.resources.EmfSupport;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Test-owned consumer observer; never distributed in the product. */
public final class IsolatedChestProbe {
  private IsolatedChestProbe() {}

  public static JsonObject inspect(StylePreviewCapture.Snapshot capture) throws Exception {
    var mc = Minecraft.getInstance();
    var active = PreviewProbeAccess.activePack();
    if (!PreviewProbeAccess.generation(active).equals(capture.resourceGeneration()))
      throw new IllegalStateException(
          "Captured resource generation retired before fixture preparation");
    var manager = mc.getResourceManager();
    var file = ClientRuntime.configuration().root().resolve("settings.json");
    byte[] before = Files.readAllBytes(file);
    boolean emf =
        !Files.readString(mc.gameDirectory.toPath().resolve("lootr-styled-probe-mode.txt"))
            .strip()
            .equals("native");
    var registrations = registrations(emf);
    int negativeLookups;
    ResourceLookupAudit.begin();
    try {
      PreviewProbeAccess.lower(active)
          .find(design.inherent.lootr_styled.ResourceId.parse("lootr_styled:probe/missing"));
    } finally {
      negativeLookups = ResourceLookupAudit.end();
    }
    if (negativeLookups < 1)
      throw new IllegalStateException("Live-resource audit negative control did not run");
    var limits =
        new GraphicLimits(
            16 * 1024 * 1024, 16 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
    var states = new JsonArray();
    try (var local = LocalFiles.open(ClientRuntime.configuration().root())) {
      for (boolean trapped : List.of(false, true))
        for (boolean looted : List.of(false, true))
          for (float lid : List.of(0f, 1f)) {
            var source =
                ChestPreviewInputs.capture(
                    ClientRuntime.targets(),
                    new ChestPreviewInputs.Subject(capture.settings(), trapped, looted, lid),
                    PreviewProbeAccess.lower(active),
                    local,
                    PreviewProbeAccess::validateModel,
                    emf ? EmfSupport.inspect(Optional.of("3.3.12")) : EmfSupport.ABSENT,
                    PreviewProbeAccess.ordinaryChestIsolated(),
                    () -> {});
            int lookups;
            design.inherent.reform.presentation.graphics.GraphicMesh mesh;
            ResourceLookupAudit.begin();
            try {
              mesh = PreviewProbeAccess.bake(source, limits, () -> {});
            } finally {
              lookups = ResourceLookupAudit.end();
            }
            if (lookups != 0)
              throw new IllegalStateException("Detached chest preparation queried live resources");
            var reference = ChestReferenceOracle.copy(trapped);
            PreviewProbeAccess.pose(reference, lid);
            var expected = PreviewProbeAccess.geometry(reference, limits);
            if (!ChestReferenceOracle.vertices(mesh)
                .equals(ChestReferenceOracle.vertices(expected)))
              throw new IllegalStateException(
                  "Detached chest geometry differs from the loaded reference pose: "
                      + trapped
                      + "/"
                      + lid
                      + " (vertices "
                      + mesh.vertices().size()
                      + "/"
                      + expected.vertices().size()
                      + ")");
            if (mesh.isEmpty())
              throw new IllegalStateException("The qualified chest unexpectedly became invisible");
            var state = new JsonObject();
            state.addProperty("trapped", trapped);
            state.addProperty("looted", looted);
            state.addProperty("lidProgress", lid);
            state.addProperty(
                "rigAnimationsEvaluated", source.poseFacts().evaluatesRigAnimations());
            state.addProperty("worldInputsUsed", source.poseFacts().usesWorldState());
            state.addProperty("rig", source.rig().map(Object::toString).orElse("native"));
            state.addProperty("texture", source.texture().toString());
            state.addProperty("vertices", mesh.vertices().size());
            state.addProperty("triangles", mesh.triangles().size());
            state.addProperty("matchesLoadedReferenceGeometry", true);
            states.add(state);
          }
    }
    if (!Arrays.equals(before, Files.readAllBytes(file))
        || manager != mc.getResourceManager()
        || PreviewProbeAccess.activePack() != active
        || !registrations.equals(registrations(emf)))
      throw new IllegalStateException(
          "Isolated chest preparation changed settings, resources, or EMF registrations");
    var result = new JsonObject();
    result.add("states", states);
    result.addProperty("settingsAndResourcesUnchanged", true);
    result.addProperty("registrationsUnchanged", true);
    result.addProperty("liveResourceLookups", 0);
    result.addProperty("lookupNegativeControl", negativeLookups);
    result.addProperty("staticReferencePose", true);
    result.addProperty("rigAnimationsEvaluated", false);
    result.addProperty("worldInputsUsed", false);
    result.addProperty("gpuPrepared", false);
    return result;
  }

  private static Map<String, List<Object>> registrations(boolean emf) throws Exception {
    if (!emf) return Map.of();
    var type = Class.forName("traben.entity_model_features.EMFManager");
    var manager = type.getMethod("getInstance").invoke(null);
    var result = new HashMap<String, List<Object>>();
    for (String name :
        List.of("rootPartsPerEntityTypeForDebug", "rootPartsPerEntityTypeForVariation")) {
      var map = (Map<?, ?>) type.getField(name).get(manager);
      var entries = new ArrayList<Object>();
      for (var entry : map.entrySet()) {
        entries.add(entry.getKey());
        Object value = entry.getValue();
        entries.add(
            value instanceof java.util.Collection<?> collection ? List.copyOf(collection) : value);
      }
      result.put(name, List.copyOf(entries));
    }
    return Map.copyOf(result);
  }
}
