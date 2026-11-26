package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.presentation.graphics.CapturedGraphicRequest;
import java.util.ArrayList;
import java.util.List;

/** Passive bounded input observations; never retains a capture, scene, source, or native lease. */
public final class SemanticProfileAudit {
  private record Input(String basis, ClientSettings candidate) {}

  private static final List<Input> inputs = new ArrayList<>();
  private static boolean enabled;

  private SemanticProfileAudit() {}

  static void begin() {
    inputs.clear();
    enabled = true;
  }

  public static void observe(CapturedGraphicRequest<?, ?> request) {
    if (!enabled) return;
    if (inputs.size() >= 32)
      throw new IllegalStateException("Semantic input observer exceeded bound");
    var candidate = request.input().candidate().value();
    if (!(candidate instanceof ClientSettings settings))
      throw new IllegalStateException("Semantic provider received a different document");
    inputs.add(new Input(request.input().basis().kind().name(), settings));
  }

  static boolean last(String basis, ClientSettings expected) {
    return !inputs.isEmpty() && inputs.getLast().equals(new Input(basis, expected));
  }

  static int count() {
    return inputs.size();
  }

  static JsonArray report() {
    var result = new JsonArray();
    for (var input : inputs) {
      var row = new JsonObject();
      row.addProperty("basis", input.basis());
      row.addProperty("enabled", input.candidate().enabled());
      row.addProperty("models", input.candidate().models().toString());
      row.addProperty("artwork", input.candidate().artwork().toString());
      row.addProperty("freshObjects", input.candidate().fresh().name());
      result.add(row);
    }
    return result;
  }
}
