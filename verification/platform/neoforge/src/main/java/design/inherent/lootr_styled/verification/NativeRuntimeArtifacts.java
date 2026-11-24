package design.inherent.lootr_styled.verification;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;

/** Actual packaged files from the public loader service, without private module reflection. */
final class NativeRuntimeArtifacts {
  private NativeRuntimeArtifacts() {}

  static Map<String, Path> roots() {
    var result = new LinkedHashMap<String, Path>();
    for (var id : List.of("lootr_styled", "reform")) {
      var mod = ModList.get().getModFileById(id);
      if (mod == null) throw new IllegalStateException("Missing runtime mod: " + id);
      result.put(id, mod.getFile().getFilePath());
    }
    return result;
  }
}
