package design.inherent.lootr_styled.profile;

import design.inherent.lootr_styled.ResourceId;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Captured declarative overrides; missing slots inherit rather than selecting another profile. */
public record StyleProfile(
    ResourceId id,
    String title,
    Map<ResourceId, ResourceId> models,
    Map<ResourceId, ResourceId> artwork) {
  public static final int MAX_MAPPINGS = 128;

  public StyleProfile {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(title, "title");
    if (title.isBlank()
        || title.codePointCount(0, title.length()) > 160
        || title.codePoints().anyMatch(Character::isISOControl)
        || !StandardCharsets.UTF_8.newEncoder().canEncode(title)) {
      throw new IllegalArgumentException(
          "Profile title must be readable text of at most 160 characters");
    }
    Objects.requireNonNull(models, "models");
    Objects.requireNonNull(artwork, "artwork");
    if ((long) models.size() + artwork.size() > MAX_MAPPINGS) {
      throw new IllegalArgumentException("Profile has too many resource mappings");
    }
    models = immutable(models);
    artwork = immutable(artwork);
    for (ResourceId key : models.keySet()) {
      if (artwork.containsKey(key)) {
        throw new IllegalArgumentException("A profile target cannot be both model and artwork");
      }
    }
  }

  private static Map<ResourceId, ResourceId> immutable(Map<ResourceId, ResourceId> value) {
    Objects.requireNonNull(value, "resources");
    var sorted = new TreeMap<ResourceId, ResourceId>(Comparator.comparing(ResourceId::toString));
    value.forEach(
        (target, source) ->
            sorted.put(Objects.requireNonNull(target), Objects.requireNonNull(source)));
    return Collections.unmodifiableMap(sorted);
  }
}
