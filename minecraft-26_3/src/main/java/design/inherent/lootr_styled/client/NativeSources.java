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
  private final java.util.Map<String, Integer> metadataOrder;

  NativeSources(ResourceManager sources) {
    this(sources, false);
  }

  NativeSources(ResourceManager sources, boolean pairedMetadata) {
    this.sources = sources;
    if (pairedMetadata) {
      var order = new java.util.HashMap<String, Integer>();
      sources.listPacks().forEach(pack -> order.put(pack.packId(), order.size()));
      metadataOrder = java.util.Map.copyOf(order);
    } else metadataOrder = null;
  }

  @Override
  public Optional<Source> find(ResourceId id) {
    var resource = sources.getResource(Identifier.parse(id.toString()));
    if (resource.isPresent() && metadataOrder != null && id.path().endsWith(".png.mcmeta")) {
      var image =
          sources.getResource(
              Identifier.parse(id.toString().substring(0, id.toString().length() - 7)));
      if (image.isEmpty()) return Optional.empty();
      String owner = image.orElseThrow().sourcePackId();
      String companion = resource.orElseThrow().sourcePackId();
      if (!owner.equals(companion)) {
        Integer imageOrder = metadataOrder.get(owner), metaOrder = metadataOrder.get(companion);
        if (imageOrder == null || metaOrder == null)
          throw new IllegalArgumentException("Cannot resolve texture metadata ownership");
        if (metaOrder < imageOrder) return Optional.empty();
      }
    }
    return resource.map(NativeSources::source);
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
                      || (pack.namespace().equals("fabric") || pack.namespace().equals("vanilla"))
                          && pack.id().equals("lootr")) return Origin.LOOTR;
                  if (pack.namespace().equals("minecraft")) return Origin.MINECRAFT;
                  return Origin.OTHER;
                })
            .orElse(Origin.OTHER);
    return new Source(origin, resource.sourcePackId(), resource::open);
  }
}
