package design.inherent.lootr_styled.verification;

import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;

/** Passive client-thread evidence that a particular workflow drew in a completed native loop. */
public final class FrameAudit {
  private static volatile long completedLoops;
  private static long rootDraws, lastRootLoop;
  private static Object drawn;
  private static WeakReference<Object> lastRoot = new WeakReference<>(null);

  private FrameAudit() {}

  public static void beginLoop() {
    drawn = null;
  }

  public static void drew(Object root) {
    drawn = root;
  }

  public static void completeLoop(Minecraft mc) {
    completedLoops++;
    if (drawn != null && drawn == NativeBindings.screen(mc) && NativeBindings.unobscured(mc)) {
      rootDraws++;
      lastRootLoop = completedLoops;
      if (lastRoot.get() != drawn) lastRoot = new WeakReference<>(drawn);
    }
    drawn = null;
  }

  static long sequence() {
    return completedLoops;
  }

  static long rootSequence(Object expected) {
    return lastRootLoop == completedLoops && lastRoot.get() == expected ? rootDraws : -1;
  }
}
