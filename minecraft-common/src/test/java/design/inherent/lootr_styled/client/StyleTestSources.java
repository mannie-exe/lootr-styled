package design.inherent.lootr_styled.client;

import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.owner.OwnerContext;
import java.nio.file.Path;

final class StyleTestSources {
  private StyleTestSources() {}

  static void initialize(Path directory) {
    var store = new StyleSettingsStore(directory);
    try (var context =
        new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD)) {
      var owner = new FileOwner<>(context, store, 16);
      try (var session = owner.open(context.access(() -> true, () -> true))) {
        session.initialize().toCompletableFuture().join();
      }
    }
  }
}
