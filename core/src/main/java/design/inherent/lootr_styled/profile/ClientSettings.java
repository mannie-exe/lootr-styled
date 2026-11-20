package design.inherent.lootr_styled.profile;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.JsonDocuments;
import java.util.Set;

/** Requested identities are persisted by the file owner, never rewritten by fallback. */
public record ClientSettings(boolean enabled, ResourceId models, ResourceId artwork, Fresh fresh) {
  public static final ResourceId DEFAULT = ResourceId.parse("lootr_styled:default");
  public static final ResourceId VANILLA = ResourceId.parse("lootr_styled:vanilla");
  public static final ClientSettings DEFAULTS =
      new ClientSettings(true, DEFAULT, VANILLA, Fresh.AUTO);
  public static final String STARTER =
      """
      {
        "format": 1,
        "enabled": true,
        "models": "lootr_styled:default",
        "artwork": "lootr_styled:vanilla",
        "freshObjects": "AUTO"
      }
      """;

  public ClientSettings {
    java.util.Objects.requireNonNull(models);
    java.util.Objects.requireNonNull(artwork);
    java.util.Objects.requireNonNull(fresh);
  }

  public static ClientSettings parse(byte[] bytes) {
    if (bytes.length > 16384) throw new IllegalArgumentException("Settings exceed 16 KiB");
    var json = JsonDocuments.object(bytes);
    if (!Set.of("format", "enabled", "models", "artwork", "freshObjects").containsAll(json.keySet())
        || !json.has("format")
        || !json.get("format").toString().equals("1")) {
      throw new IllegalArgumentException("Unsupported settings format or field");
    }
    boolean enabled = DEFAULTS.enabled();
    if (json.has("enabled")) {
      if (!json.get("enabled").isJsonPrimitive()
          || !json.getAsJsonPrimitive("enabled").isBoolean()) {
        throw new IllegalArgumentException("enabled must be boolean");
      }
      enabled = json.get("enabled").getAsBoolean();
    }
    return new ClientSettings(
        enabled,
        json.has("models") ? ResourceId.parse(string(json, "models")) : DEFAULTS.models(),
        json.has("artwork") ? ResourceId.parse(string(json, "artwork")) : DEFAULTS.artwork(),
        json.has("freshObjects") ? Fresh.valueOf(string(json, "freshObjects")) : DEFAULTS.fresh());
  }

  private static String string(com.google.gson.JsonObject json, String field) {
    var value = json.get(field);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException(field + " must be a string");
    }
    return value.getAsString();
  }

  public enum Fresh {
    AUTO,
    ON,
    OFF
  }
}
