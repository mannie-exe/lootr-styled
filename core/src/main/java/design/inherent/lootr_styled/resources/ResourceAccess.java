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
