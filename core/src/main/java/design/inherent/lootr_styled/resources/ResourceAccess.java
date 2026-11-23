package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/** Effective lower-stack lookup. Sources are borrowed; only streams opened here are owned. */
public interface ResourceAccess {
  Optional<Source> find(ResourceId id);

  Map<ResourceId, Source> list(String directory);

  /** Check the borrowing scope before lookup and stream entry; never close the borrowed source. */
  default ResourceAccess guarded(Runnable checkpoint) {
    java.util.Objects.requireNonNull(checkpoint);
    var delegate = this;
    return new ResourceAccess() {
      private Source scoped(Source source) {
        return new Source(
            source.origin(),
            source.description(),
            () -> {
              checkpoint.run();
              return source.opener().open();
            });
      }

      @Override
      public Optional<Source> find(ResourceId id) {
        checkpoint.run();
        return delegate.find(id).map(this::scoped);
      }

      @Override
      public Map<ResourceId, Source> list(String directory) {
        checkpoint.run();
        var result = new java.util.LinkedHashMap<ResourceId, Source>();
        delegate.list(directory).forEach((id, source) -> result.put(id, scoped(source)));
        return Map.copyOf(result);
      }
    };
  }

  record Source(Origin origin, String description, Opener opener) {
    public Source {
      java.util.Objects.requireNonNull(origin);
      java.util.Objects.requireNonNull(description);
      java.util.Objects.requireNonNull(opener);
    }
  }

  enum Origin {
    MINECRAFT,
    LOOTR,
    LOCAL,
    OTHER
  }

  @FunctionalInterface
  interface Opener {
    InputStream open() throws IOException;
  }
}
