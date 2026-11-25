package design.inherent.lootr_styled.verification;

import design.inherent.reform.minecraft.client.ClientGraphicHost;
import java.util.ArrayList;
import java.util.List;

/** Passive public-request observations and one test-only pause in actual domain material work. */
public final class ProductionPreparationAudit {
  record Entry(ClientGraphicHost.View<?, ?> view, ClientGraphicHost.Attempt attempt) {}

  private static final List<Entry> entries = new ArrayList<>();
  private static volatile PreviewWorkerGate worker;
  private static PreviewWorkerGate.Hold held;
  private static Thread client;

  private ProductionPreparationAudit() {}

  static void begin() {
    if (worker != null) throw new IllegalStateException("Production worker audit already active");
    client = Thread.currentThread();
    entries.clear();
    worker = new PreviewWorkerGate();
  }

  static PreviewWorkerGate.Hold arm() {
    held = worker.arm();
    return held;
  }

  public static void materialEntered() {
    var gate = worker;
    if (gate != null) gate.run();
  }

  public static void requested(
      ClientGraphicHost.View<?, ?> view, ClientGraphicHost.Attempt attempt) {
    if (worker == null) return;
    if (Thread.currentThread() != client || entries.size() >= 16)
      throw new IllegalStateException(
          "Production request observer exceeded its thread/count bound");
    entries.add(new Entry(view, attempt));
  }

  static int requests() {
    return entries.size();
  }

  static Entry latest() {
    return entries.getLast();
  }

  static void stop() {
    worker = null;
    held = null;
    entries.clear();
    client = null;
  }

  static void abort() {
    if (held != null) held.release();
    stop();
  }
}
