package design.inherent.lootr_styled.verification;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only pause inside the actual domain worker; it never blocks the client thread. */
final class PreviewWorkerGate implements Runnable {
  static final class Hold {
    private final CountDownLatch release = new CountDownLatch(1);
    volatile boolean entered, exited;
    volatile String thread;

    void release() {
      release.countDown();
    }
  }

  private final Thread client = Thread.currentThread();
  private final AtomicReference<Hold> next = new AtomicReference<>();

  Hold arm() {
    var hold = new Hold();
    if (!next.compareAndSet(null, hold))
      throw new IllegalStateException("Worker gate already armed");
    return hold;
  }

  @Override
  public void run() {
    var hold = next.getAndSet(null);
    if (hold == null) return;
    if (Thread.currentThread() == client)
      throw new IllegalStateException("Preview preparation reached the gate on the client");
    hold.thread = Thread.currentThread().getName();
    hold.entered = true;
    boolean interrupted = false;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    try {
      while (hold.release.getCount() != 0) {
        if (System.nanoTime() >= deadline) throw new IllegalStateException("Worker gate timed out");
        try {
          hold.release.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
          // Simulate an entered native task that does not stop merely because cancellation
          // was requested. Restore interruption after the explicit test release.
          interrupted = true;
        }
      }
    } finally {
      hold.exited = true;
      if (interrupted) Thread.currentThread().interrupt();
    }
  }
}
