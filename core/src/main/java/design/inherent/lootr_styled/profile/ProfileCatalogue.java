package design.inherent.lootr_styled.profile;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.BarrelCompiler;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.ResourceAccess;
import design.inherent.lootr_styled.resources.ResourceInputs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A reload-scoped catalogue; source adapters share profile parsing and compilation. */
public final class ProfileCatalogue {
  public static final String DIRECTORY = "lootr_styled/profiles";
  public static final String LOCAL_NAMESPACE = "lootr_styled_local";
  private final Map<ResourceId, Entry> entries;
  private final List<String> diagnostics;

  private ProfileCatalogue(Map<ResourceId, Entry> entries, List<String> diagnostics) {
    this.entries = Map.copyOf(entries);
    this.diagnostics = List.copyOf(diagnostics);
  }

  public Optional<Entry> find(ResourceId id) {
    return Optional.ofNullable(entries.get(id));
  }

  public List<Entry> entries() {
    return entries.values().stream()
        .sorted(Comparator.comparing(e -> e.profile().id().toString()))
        .toList();
  }

  public List<String> diagnostics() {
    return diagnostics;
  }

  public static ProfileCatalogue discover(
      ContainerTargets targets,
      ResourceAccess nativeSources,
      LocalFiles local,
      ResourceInputs.Budget budget) {
    var entries = new HashMap<ResourceId, Entry>();
    var diagnostics = new ArrayList<String>();
    var defaultModels = new HashMap<ResourceId, ResourceId>();
    for (var barrel : targets.barrels())
      defaultModels.put(barrel.target(), BarrelCompiler.DEFAULT_MODEL);
    entries.put(
        ClientSettings.DEFAULT,
        new Entry(
            new StyleProfile(ClientSettings.DEFAULT, "Default", defaultModels, Map.of()),
            nativeSources,
            "Built-in"));
    entries.put(
        ClientSettings.VANILLA,
        new Entry(
            new StyleProfile(ClientSettings.VANILLA, "Inherited", Map.of(), Map.of()),
            nativeSources,
            "Built-in"));
    var metadata = new ResourceInputs(nativeSources, budget);
    var declared = nativeSources.list(DIRECTORY);
    if (declared.size() > 64)
      throw new ResourceInputs.LimitExceeded("Pack profile count exceeds 64");
    for (var item :
        declared.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceId::toString)))
            .toList()) {
      try {
        ResourceId resource = item.getKey();
        String path = resource.path();
        if (!path.startsWith(DIRECTORY + "/") || !path.endsWith(".json"))
          throw new IllegalArgumentException("Invalid descriptor path");
        var id =
            new ResourceId(
                resource.namespace(), path.substring(DIRECTORY.length() + 1, path.length() - 5));
        if (id.namespace().equals("lootr_styled") || id.namespace().equals(LOCAL_NAMESPACE))
          throw new IllegalArgumentException("Reserved profile identity");
        var captured = metadata.require(resource);
        var profile = ProfileCodec.parse(id, captured.bytes());
        entries.put(id, new Entry(profile, nativeSources, captured.source().description()));
      } catch (ResourceInputs.LimitExceeded ex) {
        throw ex;
      } catch (IllegalArgumentException ex) {
        diagnostics.add(item.getKey() + ": invalid pack profile (" + ex.getMessage() + ")");
      }
    }
    if (local != null) {
      try {
        for (String slug : local.profiles()) {
          try {
            var descriptor =
                local.read(Path.of(slug, "profile.json"), ProfileCodec.MAX_METADATA_BYTES);
            if (descriptor.isEmpty()) continue;
            budget.lookup();
            budget.bytes(descriptor.orElseThrow().length);
            var id = new ResourceId(LOCAL_NAMESPACE, slug);
            var profile = ProfileCodec.parse(id, descriptor.orElseThrow());
            ResourceAccess sources =
                new ResourceAccess() {
                  @Override
                  public Optional<Source> find(ResourceId resource) {
                    try {
                      var value =
                          local.read(
                              Path.of(slug, "assets", resource.namespace(), resource.path()),
                              ResourceInputs.MAX_FILE_BYTES);
                      return value.isPresent()
                          ? Optional.of(
                              new Source(
                                  Origin.LOCAL,
                                  "Local: " + slug,
                                  () -> new ByteArrayInputStream(value.orElseThrow())))
                          : nativeSources.find(resource);
                    } catch (IOException ex) {
                      throw new IllegalArgumentException("Local resource refused: " + resource, ex);
                    }
                  }

                  @Override
                  public Map<ResourceId, Source> list(String directory) {
                    return Map.of();
                  }
                };
            entries.put(id, new Entry(profile, sources, "Local: " + slug));
          } catch (ResourceInputs.LimitExceeded ex) {
            throw ex;
          } catch (IOException | IllegalArgumentException ex) {
            diagnostics.add("Local " + slug + ": invalid profile (" + ex.getMessage() + ")");
          }
        }
      } catch (IOException ex) {
        diagnostics.add("Local catalogue unavailable (" + ex.getMessage() + ")");
      }
    }
    return new ProfileCatalogue(entries, diagnostics);
  }

  public record Entry(StyleProfile profile, ResourceAccess sources, String description) {}
}
