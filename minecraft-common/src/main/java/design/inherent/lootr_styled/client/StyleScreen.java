package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientConfiguration;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.ProfileExporter;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local client choices. Drafts survive resize/subscreens; only Apply writes and reloads. */
public final class StyleScreen extends NativePanel {
  private final Screen parent;
  private ClientConfiguration.Snapshot baseline;
  private boolean enabled;
  private ClientSettings.Fresh fresh;
  private String models, artwork;
  private String readProblem;
  private Button apply;
  private boolean busy;

  public StyleScreen(Screen parent) {
    super(Component.literal("Lootr: Styled"));
    this.parent = parent;
    try {
      baseline = ClientRuntime.configuration().snapshot();
      draft(baseline.settings());
    } catch (java.io.IOException | IllegalArgumentException ex) {
      readProblem = ex.getMessage();
      draft(ClientSettings.DEFAULTS);
    }
  }

  private void draft(ClientSettings value) {
    enabled = value.enabled();
    fresh = value.fresh();
    models = value.models().toString();
    artwork = value.artwork().toString();
  }

  @Override
  protected void init() {
    int w = Math.min(380, width - 24), x = (width - w) / 2, half = (w - 4) / 2;
    label(title, x, 10, w);
    addRenderableWidget(
        Button.builder(
                Component.literal("Enabled: " + enabled),
                button -> {
                  enabled = !enabled;
                  button.setMessage(Component.literal("Enabled: " + enabled));
                  validate();
                })
            .bounds(x, 32, half, 20)
            .tooltip(
                Tooltip.create(
                    Component.literal("Client presentation only. Lootr gameplay is unchanged.")))
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("Fresh Objects: " + fresh),
                button -> {
                  fresh = ClientSettings.Fresh.values()[(fresh.ordinal() + 1) % 3];
                  button.setMessage(Component.literal("Fresh Objects: " + fresh));
                  validate();
                })
            .bounds(x + half + 4, 32, half, 20)
            .tooltip(
                Tooltip.create(
                    Component.literal(
                        "AUTO follows supported installed rigs; ON reports missing input; OFF keeps native parts. Explicit Lootr profiles remain separate.")))
            .build());
    label(Component.literal("Models — requested profile"), x, 56, w);
    field(
        models,
        x,
        70,
        w - 66,
        value -> {
          models = value;
          validate();
        });
    button(
        "Choose",
        x + w - 62,
        70,
        62,
        () ->
            NativeUi.open(
                new ProfilePicker(
                    this,
                    value -> {
                      models = value;
                      NativeUi.open(this);
                    })));
    label(Component.literal("Artwork — requested profile"), x, 96, w);
    field(
        artwork,
        x,
        110,
        w - 66,
        value -> {
          artwork = value;
          validate();
        });
    button(
        "Choose",
        x + w - 62,
        110,
        62,
        () ->
            NativeUi.open(
                new ProfilePicker(
                    this,
                    value -> {
                      artwork = value;
                      NativeUi.open(this);
                    })));
    apply = button("Apply & reload", x, 140, half, this::save);
    button(
        "Defaults (draft)",
        x + half + 4,
        140,
        half,
        () -> {
          draft(ClientSettings.DEFAULTS);
          rebuildWidgets();
        });
    button("Refresh resources", x, 164, half, this::refresh);
    button(
        "Open profile folder",
        x + half + 4,
        164,
        half,
        () -> NativeUi.openPath(ClientRuntime.configuration().root()));
    button("Export Default", x, 188, half, this::export);
    button(
        "Effective values / reasons",
        x + half + 4,
        188,
        half,
        () ->
            details(
                readProblem == null
                    ? ClientRuntime.status()
                    : List.of(
                        "Settings draft preserved: " + readProblem,
                        "Correct the file, then reopen this screen.")));
    button("Done", x, height - 24, w, this::onClose);
    validate();
  }

  private void label(Component text, int x, int y, int w) {
    addRenderableWidget(new StringWidget(x, y, w, 12, text, font));
  }

  private void field(
      String value, int x, int y, int w, java.util.function.Consumer<String> changed) {
    var field =
        new EditBox(
            font,
            x,
            y,
            w,
            20,
            Component.literal(y == 70 ? "Models profile ID" : "Artwork profile ID"));
    field.setMaxLength(256);
    field.setValue(value);
    field.setResponder(changed);
    addRenderableWidget(field);
  }

  private Button button(String text, int x, int y, int w, Runnable action) {
    return addRenderableWidget(
        Button.builder(Component.literal(text), unused -> action.run())
            .bounds(x, y, w, 20)
            .build());
  }

  private ClientSettings value() {
    return new ClientSettings(enabled, ResourceId.parse(models), ResourceId.parse(artwork), fresh);
  }

  private void validate() {
    if (apply == null) return;
    try {
      value();
      apply.active = baseline != null && !busy;
    } catch (IllegalArgumentException ex) {
      apply.active = false;
    }
  }

  private void save() {
    try {
      baseline = ClientRuntime.configuration().save(value(), baseline);
      readProblem = null;
      refresh();
    } catch (java.io.IOException | IllegalArgumentException ex) {
      details(List.of("Settings were not saved: " + ex.getMessage()));
    }
  }

  private void refresh() {
    if (busy) return;
    busy = true;
    validate();
    minecraft
        .reloadResourcePacks()
        .whenComplete(
            (unused, failure) ->
                minecraft.execute(
                    () -> {
                      busy = false;
                      validate();
                      details(
                          failure == null
                              ? ClientRuntime.status()
                              : List.of(
                                  "Resource reload failed: " + failure.getClass().getSimpleName(),
                                  "Review the game log. Native resources own recovery."));
                    }));
  }

  private void export() {
    try (var input =
        StyleScreen.class.getResourceAsStream(
            "/assets/lootr_styled/models/block/barrel_clasps.json")) {
      if (input == null) throw new java.io.IOException("Bundled barrel model unavailable");
      var destination =
          ProfileExporter.exportDefault(
              ClientRuntime.configuration(), ClientRuntime.targets(), input.readNBytes(2097153));
      details(
          List.of(
              "Exported geometry only: " + destination.getFileName(),
              "Refresh, then choose lootr_styled_local:exported-default to use this editable copy.",
              "Export never replaces an existing directory or bundles artwork."));
    } catch (java.io.IOException | IllegalArgumentException ex) {
      details(List.of("Export refused: " + ex.getMessage()));
    }
  }

  private void details(List<String> lines) {
    NativeUi.open(new ReasonScreen(this, lines));
  }

  @Override
  public void onClose() {
    boolean changed;
    try {
      changed = baseline != null && !value().equals(baseline.settings());
    } catch (IllegalArgumentException ex) {
      changed = true;
    }
    if (changed)
      NativeUi.open(
          new ConfirmScreen(
              yes -> NativeUi.open(yes ? parent : this),
              Component.literal("Discard this draft?"),
              Component.literal("Only Apply saves settings. Profile files are unaffected.")));
    else NativeUi.open(parent);
  }
}
