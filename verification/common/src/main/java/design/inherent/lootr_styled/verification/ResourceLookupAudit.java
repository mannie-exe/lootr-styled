package design.inherent.lootr_styled.verification;

/** Thread-scoped observer only; never changes the resource manager or lookup results. */
public final class ResourceLookupAudit {
  private static final ThreadLocal<Integer> LOOKUPS = new ThreadLocal<>();

  private ResourceLookupAudit() {}

  public static void begin() {
    if (LOOKUPS.get() != null) throw new IllegalStateException("Resource audit already active");
    LOOKUPS.set(0);
  }

  public static void observed() {
    var count = LOOKUPS.get();
    if (count != null) LOOKUPS.set(count + 1);
  }

  public static int end() {
    var count = LOOKUPS.get();
    LOOKUPS.remove();
    if (count == null) throw new IllegalStateException("Resource audit was not active");
    return count;
  }
}
