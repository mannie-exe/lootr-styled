package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import java.util.Map;
import java.util.Optional;

/** The isolated compiler's output over its borrowed lower sources, never an installed pack. */
final class PreviewResources {
  private PreviewResources() {}

  static ResourceAccess overlay(FrozenResources compiled, ResourceAccess lower) {
    return new ResourceAccess() {
      @Override
      public Optional<Source> find(ResourceId id) {
        if (compiled.contains(id))
          return Optional.of(
              new Source(
                  Origin.LOCAL,
                  "Isolated draft compilation",
                  () -> compiled.open(id).orElseThrow()));
        if (id.path().endsWith(".png.mcmeta")) {
          var image =
              new ResourceId(id.namespace(), id.path().substring(0, id.path().length() - 7));
          if (compiled.contains(image)) return Optional.empty();
        }
        return lower.find(id);
      }

      @Override
      public Map<ResourceId, Source> list(String directory) {
        throw new UnsupportedOperationException("Preview capture resolves explicit dependencies");
      }
    };
  }
}
