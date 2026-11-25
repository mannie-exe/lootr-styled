package design.inherent.lootr_styled.client;

import design.inherent.reform.definition.FieldKey;
import design.inherent.reform.definition.RecordType;
import design.inherent.reform.value.ValueTypes;
import java.util.Objects;

/** View parameters only; the complete settings candidate is captured by the shared host. */
record StyleGraphicSubject(Container container, boolean looted, boolean open, float lid) {
  enum Container {
    CHEST,
    TRAPPED_CHEST,
    BARREL
  }

  private static final FieldKey<Container> CONTAINER =
      FieldKey.of("container", ValueTypes.enumeration(Container.class));
  private static final FieldKey<Boolean> LOOTED = FieldKey.of("looted", ValueTypes.bool());
  private static final FieldKey<Boolean> OPEN = FieldKey.of("open", ValueTypes.bool());
  private static final FieldKey<Float> LID = FieldKey.of("lid", ValueTypes.floating(0, 1));
  static final RecordType<StyleGraphicSubject> TYPE =
      RecordType.<StyleGraphicSubject>builder(
              v ->
                  new StyleGraphicSubject(v.get(CONTAINER), v.get(LOOTED), v.get(OPEN), v.get(LID)))
          .field(CONTAINER, StyleGraphicSubject::container)
          .field(LOOTED, StyleGraphicSubject::looted)
          .field(OPEN, StyleGraphicSubject::open)
          .field(LID, StyleGraphicSubject::lid)
          .build();

  StyleGraphicSubject {
    Objects.requireNonNull(container);
    if (!Float.isFinite(lid) || lid < 0 || lid > 1)
      throw new IllegalArgumentException("Lid pose must be between zero and one");
  }

  /** The factored Open control follows the active container's native pose. */
  boolean opened() {
    return container == Container.BARREL ? open : lid > 0;
  }

  StyleGraphicSubject withContainer(Container value) {
    Objects.requireNonNull(value);
    if (value == container) return this;
    if (value == Container.BARREL) return new StyleGraphicSubject(value, looted, opened(), lid);
    if (container == Container.BARREL)
      return new StyleGraphicSubject(value, looted, open, open ? 1 : 0);
    return new StyleGraphicSubject(value, looted, open, lid);
  }

  StyleGraphicSubject withLooted(boolean value) {
    return new StyleGraphicSubject(container, value, open, lid);
  }

  StyleGraphicSubject withOpened(boolean value) {
    return container == Container.BARREL
        ? new StyleGraphicSubject(container, looted, value, lid)
        : new StyleGraphicSubject(container, looted, open, value ? 1 : 0);
  }
}
