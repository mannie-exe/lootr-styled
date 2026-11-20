package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.ResourceAccess;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Uses the native effective stack, including filters and overlays; never owns those packs. */
final class NativeSources implements ResourceAccess {
  private final ResourceManager sources;

  NativeSources(ResourceManager sources) {
    this.sources = sources;
  }

  @Override
  public Optional<Source> find(ResourceId id) {
    return sources.getResource(Identifier.parse(id.toString())).map(NativeSources::source);
  }

  @Override
  public java.util.Map<ResourceId, Source> list(String directory) {
    var result = new java.util.LinkedHashMap<ResourceId, Source>();
    sources
        .listResources(directory, id -> id.getPath().endsWith(".json"))
        .forEach(
            (id, resource) -> {
              if (result.size() >= 64)
                throw new IllegalArgumentException("Profile catalogue exceeds 64 descriptors");
              result.put(ResourceId.parse(id.toString()), source(resource));
            });
    return java.util.Map.copyOf(result);
  }

  private static Source source(Resource resource) {
    Origin origin =
        resource
            .knownPackInfo()
            .map(
                pack -> {
                  if (pack.namespace().equals("neoforge") && pack.id().equals("mod/lootr")
                      || pack.namespace().equals("fabric") && pack.id().equals("lootr"))
                    return Origin.LOOTR;
                  if (pack.namespace().equals("minecraft")) return Origin.MINECRAFT;
                  return Origin.OTHER;
                })
            .orElse(Origin.OTHER);
    return new Source(origin, resource.sourcePackId(), resource::open);
  }
}
