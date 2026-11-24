package design.inherent.lootr_styled.verification;

/** One-shot verification-only failure at Styled's native reload boundary. */
public final class ReloadFault {
  private static boolean armed;
  private static int calls, failures;

  private ReloadFault() {}

  static void arm() {
    if (armed) throw new IllegalStateException("Reload fault already armed");
    armed = true;
  }

  public static boolean enter() {
    calls++;
    if (!armed) return false;
    armed = false;
    failures++;
    return true;
  }

  static int calls() {
    return calls;
  }

  static int failures() {
    return failures;
  }
}
