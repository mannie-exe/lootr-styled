package design.inherent.lootr_styled.verification;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Actual loader roots, including Loom remaps; origin declarations alone are insufficient. */
final class NativeRuntimeArtifacts {
  private NativeRuntimeArtifacts() {}

  static Map<String, Path> roots() {
    var result = new LinkedHashMap<String, Path>();
    for (var id : List.of("lootr_styled", "reform")) {
      var roots = FabricLoader.getInstance().getModContainer(id).orElseThrow().getRootPaths();
      if (roots.size() != 1) throw new IllegalStateException("Ambiguous runtime roots: " + id);
      var uri = roots.getFirst().toUri();
      var nested = uri.getRawSchemeSpecificPart();
      int end = nested.indexOf("!/");
      if (!uri.getScheme().equals("jar") || end < 0)
        throw new IllegalStateException("Expected packaged runtime root: " + uri);
      result.put(id, Path.of(URI.create(nested.substring(0, end))));
    }
    return result;
  }
}
