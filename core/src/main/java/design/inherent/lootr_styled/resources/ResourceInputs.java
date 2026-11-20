package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** One bounded, memoized input set per compilation, never per rendered container. */
public final class ResourceInputs {
  public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
  public static final int MAX_FILES = 256;
  public static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
  private final ResourceAccess access;
  private final Map<ResourceId, Optional<Captured>> captured = new HashMap<>();
  private final Budget budget;

  public ResourceInputs(ResourceAccess access) {
    this(access, new Budget());
  }

  public ResourceInputs(ResourceAccess access, Budget budget) {
    this.access = java.util.Objects.requireNonNull(access);
    this.budget = java.util.Objects.requireNonNull(budget);
  }

  public Optional<Captured> find(ResourceId id) {
    if (captured.containsKey(id)) {
      return captured.get(id);
    }
    budget.lookup();
    var source = access.find(id);
    Optional<Captured> result = Optional.empty();
    if (source.isPresent()) {
      byte[] bytes;
      try (var stream = source.orElseThrow().opener().open()) {
        bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
      } catch (IOException ex) {
        throw new IllegalArgumentException("Cannot read resource " + id, ex);
      }
      budget.bytes(bytes.length);
      result = Optional.of(new Captured(source.orElseThrow(), bytes));
    }
    captured.put(id, result);
    return result;
  }

  public Captured require(ResourceId id) {
    return find(id).orElseThrow(() -> new IllegalArgumentException("Missing resource " + id));
  }

  public static final class Budget {
    private int files;
    private int bytes;

    public synchronized void lookup() {
      if (files >= MAX_FILES) throw new LimitExceeded("Input resource count exceeds " + MAX_FILES);
      files++;
    }

    public synchronized void bytes(int count) {
      if (count < 0 || count > MAX_FILE_BYTES || count > MAX_TOTAL_BYTES - bytes) {
        throw new LimitExceeded("Input resource byte budget exceeded");
      }
      bytes += count;
    }
  }

  public static final class LimitExceeded extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public LimitExceeded(String message) {
      super(message);
    }
  }

  public record Captured(ResourceAccess.Source source, byte[] bytes) {
    public Captured {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }
}
