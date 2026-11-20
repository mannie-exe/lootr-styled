package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.ProfileCatalogue;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoped Lootr CEM resources. Empty native declarations do not supply replacement chest geometry.
 */
final class ChestPresentation {
  private static final byte[] NATIVE =
      "{\"textureSize\":[64,64],\"models\":[]}".getBytes(StandardCharsets.UTF_8);

  private ChestPresentation() {}

  static Map<ResourceId, byte[]> compile(
      ContainerTargets targets,
      ClientSettings.Fresh choice,
      EmfSupport emf,
      ProfileCatalogue.Entry selected,
      ResourceInputs nativeInputs,
      ResourceInputs.Budget budget,
      List<String> status) {
    return compile(targets, choice, emf, selected, nativeInputs, budget, status, false);
  }

  static Map<ResourceId, byte[]> compile(
      ContainerTargets targets,
      ClientSettings.Fresh choice,
      EmfSupport emf,
      ProfileCatalogue.Entry selected,
      ResourceInputs nativeInputs,
      ResourceInputs.Budget budget,
      List<String> status,
      boolean ordinaryChestIsolated) {
    var output = new HashMap<ResourceId, byte[]>();
    if (!emf.admitted()) {
      status.add("Chest adaptation: " + emf.reason());
      return output;
    }
    var profileInputs = new ResourceInputs(selected.sources(), budget);
    for (var chest : targets.chests()) {
      var target = chest.target();
      if (targets == ContainerTargets.MODERN
          && chest.vanillaName().equals("chest")
          && !ordinaryChestIsolated) {
        status.add(target + ": native fallback; shared copper renderer isolation unavailable");
        continue;
      }
      var explicit = selected.profile().models().get(target);
      boolean existing =
          nativeInputs.find(target).isPresent() || nativeInputs.find(optifine(target)).isPresent();
      if (explicit == null && existing) {
        status.add(target + ": explicit Lootr rig kept");
        continue;
      }
      if (explicit == null && choice == ClientSettings.Fresh.OFF) {
        output.put(target, NATIVE);
        status.add(target + ": Fresh OFF; native rig declaration");
        continue;
      }
      try {
        if (explicit != null) {
          if (!explicit.path().endsWith(".jem"))
            throw new IllegalArgumentException("Chest profiles require a supported JEM definition");
          output.putAll(RigAdapter.compile(profileInputs, explicit, target, false));
          status.add(target + ": selected Lootr rig prepared");
        } else {
          ResourceId source = ResourceId.parse("minecraft:emf/cem/" + chest.vanillaName() + ".jem");
          if (nativeInputs.find(source).isEmpty()) source = optifine(source);
          if (nativeInputs.find(source).isEmpty())
            throw new IllegalArgumentException(
                "No effective vanilla chest rig; native lid movement remains");
          output.putAll(RigAdapter.compile(nativeInputs, source, target, true));
          status.add(target + ": supported effective vanilla rig prepared");
        }
      } catch (ResourceInputs.LimitExceeded ex) {
        throw ex;
      } catch (IllegalArgumentException ex) {
        if (!existing) output.put(target, NATIVE);
        status.add(target + ": native/explicit fallback (" + ex.getMessage() + ")");
      }
    }
    return output;
  }

  private static ResourceId optifine(ResourceId id) {
    return new ResourceId(id.namespace(), id.path().replaceFirst("^emf/", "optifine/"));
  }
}
