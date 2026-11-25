package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded verification-only elapsed callback measurements, never a GPU/frame-time profiler. */
public final class UiCostAudit {
  private static final AtomicLong PREPARATIONS = new AtomicLong();
  private static final AtomicLong DISCOVERIES = new AtomicLong();
  private static final long[] SAMPLES = new long[256];
  private static Object owner;
  private static Thread ownerThread;
  private static String label;
  private static long started, resourceLookups, metadataReads, preparationsInPaint;
  private static int count;

  private UiCostAudit() {}

  static long preparations() {
    return PREPARATIONS.get();
  }

  static long discoveries() {
    return DISCOVERIES.get();
  }

  public static void catalogueDiscovered() {
    DISCOVERIES.incrementAndGet();
  }

  public static void providerEntered() {
    PREPARATIONS.incrementAndGet();
    if (Thread.currentThread() == ownerThread && started != 0) preparationsInPaint++;
  }

  static void watch(Object screen, String phase) {
    if (owner != null || started != 0)
      throw new IllegalStateException("UI cost observation already active");
    owner = java.util.Objects.requireNonNull(screen);
    ownerThread = Thread.currentThread();
    label = phase;
    count = 0;
    resourceLookups = 0;
    preparationsInPaint = 0;
    metadataReads = 0;
  }

  public static void begin(Object screen) {
    if (screen != owner || count == SAMPLES.length) return;
    if (started != 0) throw new IllegalStateException("Nested observed UI callback");
    ResourceLookupAudit.begin();
    started = System.nanoTime();
  }

  public static void end(Object screen) {
    if (screen != owner || started == 0) return;
    SAMPLES[count++] = System.nanoTime() - started;
    started = 0;
    resourceLookups += ResourceLookupAudit.end();
  }

  public static void metadataRead() {
    if (Thread.currentThread() == ownerThread && started != 0) metadataReads++;
  }

  static int samples() {
    return count;
  }

  static JsonObject finish() {
    if (owner == null || started != 0 || count == 0)
      throw new IllegalStateException("No completed UI callback samples");
    var values = Arrays.copyOf(SAMPLES, count);
    Arrays.sort(values);
    var result = new JsonObject();
    result.addProperty("phase", label);
    result.addProperty("samples", count);
    result.addProperty("callbackWallMinNanos", values[0]);
    result.addProperty("callbackWallMedianNanos", values[count / 2]);
    result.addProperty("callbackWallP95Nanos", values[(int) Math.ceil(count * .95) - 1]);
    result.addProperty("callbackWallMaxNanos", values[count - 1]);
    result.addProperty("resourceManagerCallsInCallback", resourceLookups);
    result.addProperty("packMetadataReadsInCallback", metadataReads);
    result.addProperty("resourceContentLookupsInCallback", resourceLookups - metadataReads);
    result.addProperty("providerEntriesInCallback", preparationsInPaint);
    owner = null;
    ownerThread = null;
    label = null;
    return result;
  }
}
