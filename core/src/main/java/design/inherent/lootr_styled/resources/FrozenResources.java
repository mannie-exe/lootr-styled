package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * One owned resource generation. It retains bytes, never live models, sprites or source streams.
 */
public final class FrozenResources {
  public static final Limits DEFAULT_LIMITS = new Limits(256, 16L * 1024 * 1024);
  public static final FrozenResources EMPTY = new FrozenResources(Map.of(), DEFAULT_LIMITS);

  private final Map<ResourceId, byte[]> entries;
  private final long byteSize;

  public FrozenResources(Map<ResourceId, byte[]> input, Limits limits) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(limits, "limits");
    if (input.size() > limits.maxFiles()) {
      throw new IllegalArgumentException("Generated resource file budget exceeded");
    }
    var copy = new TreeMap<ResourceId, byte[]>(Comparator.comparing(ResourceId::toString));
    long used = 0;
    for (var entry : input.entrySet()) {
      ResourceId id = Objects.requireNonNull(entry.getKey(), "resource identity");
      byte[] bytes = Objects.requireNonNull(entry.getValue(), "resource bytes");
      if (copy.size() >= limits.maxFiles() || bytes.length > limits.maxBytes() - used) {
        throw new IllegalArgumentException("Generated resource budget exceeded");
      }
      used += bytes.length;
      copy.put(id, bytes.clone());
    }
    entries = Collections.unmodifiableMap(copy);
    byteSize = used;
  }

  public Set<ResourceId> ids() {
    return entries.keySet();
  }

  public boolean contains(ResourceId id) {
    return entries.containsKey(id);
  }

  public Optional<InputStream> open(ResourceId id) {
    byte[] bytes = entries.get(id);
    return bytes == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(bytes));
  }

  public long byteSize() {
    return byteSize;
  }

  public record Limits(int maxFiles, long maxBytes) {
    public Limits {
      if (maxFiles < 0 || maxBytes < 0) {
        throw new IllegalArgumentException("Resource budgets cannot be negative");
      }
    }
  }
}
