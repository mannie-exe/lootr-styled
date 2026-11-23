package design.inherent.lootr_styled.verification;

import design.inherent.reform.minecraft.client.PreparedGraphic;
import java.util.concurrent.atomic.AtomicReference;

/** One exact test lease fails only after its real native deletion method has returned. */
public final class NativeCleanupFault {
  private static final AtomicReference<Object> target = new AtomicReference<>();
  private static final RuntimeException fault =
      new IllegalStateException("Injected post-cleanup acknowledgment failure");
  private static int injected;

  private NativeCleanupFault() {}

  static void arm(PreparedGraphic lease) throws Exception {
    var field = PreparedGraphic.class.getDeclaredField("graphic");
    field.setAccessible(true);
    var client = field.get(lease);
    var nativeField = client.getClass().getDeclaredField("nativeGraphic");
    nativeField.setAccessible(true);
    if (!target.compareAndSet(null, nativeField.get(client)))
      throw new IllegalStateException("A cleanup fault is already armed");
  }

  public static void afterRelease(Object graphic) {
    if (target.compareAndSet(graphic, null)) {
      injected++;
      throw fault;
    }
  }

  static boolean caused(Throwable observed) {
    return observed == fault;
  }

  static int injected() {
    return injected;
  }
}
