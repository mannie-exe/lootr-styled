package design.inherent.lootr_styled.verification;

import design.inherent.reform.minecraft.client.ClientMeshGraphic;

/** Client-thread draw outcomes and one bounded control, cleared before its callback runs. */
public final class GraphicDrawAudit {
  private static long rendered, waiting, empty;
  private static Runnable nextRendered;
  private static java.util.function.Consumer<ClientMeshGraphic> nextGraphic;
  private static Sample last;

  public record Sample(
      java.lang.ref.WeakReference<ClientMeshGraphic> graphic,
      design.inherent.reform.presentation.graphics.GraphicCamera camera,
      int width,
      int height,
      long sequence) {}

  static Sample lastRendered() {
    return java.util.Objects.requireNonNull(last, "No rendered graphic");
  }

  private GraphicDrawAudit() {}

  public record State(long rendered, long waiting, long empty) {}

  public static State state() {
    return new State(rendered, waiting, empty);
  }

  static void afterNextRendered(Runnable action) {
    if (nextRendered != null) throw new IllegalStateException("A draw control is already armed");
    nextRendered = java.util.Objects.requireNonNull(action);
  }

  static void afterNextRenderedGraphic(java.util.function.Consumer<ClientMeshGraphic> action) {
    if (nextGraphic != null) throw new IllegalStateException("A graphic control is already armed");
    nextGraphic = java.util.Objects.requireNonNull(action);
  }

  public static void observed(
      ClientMeshGraphic graphic,
      ClientMeshGraphic.DrawOutcome outcome,
      design.inherent.reform.presentation.graphics.GraphicCamera camera,
      int width,
      int height) {
    switch (outcome) {
      case RENDERED -> rendered++;
      case WAITING -> waiting++;
      case EMPTY -> empty++;
    }
    if (outcome == ClientMeshGraphic.DrawOutcome.RENDERED) {
      last =
          new Sample(new java.lang.ref.WeakReference<>(graphic), camera, width, height, rendered);
      var action = nextRendered;
      nextRendered = null;
      var observation = nextGraphic;
      nextGraphic = null;
      if (action != null) action.run();
      if (observation != null) observation.accept(graphic);
    }
  }
}
