package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.minecraft.client.ClientFileSettings;
import design.inherent.reform.minecraft.client.ClientForm;
import design.inherent.reform.minecraft.client.ClientFormOptions;
import design.inherent.reform.minecraft.client.ClientRegistration;
import design.inherent.reform.minecraft.client.FormInformation;
import design.inherent.reform.minecraft.client.ReferenceTitles;
import design.inherent.reform.owner.ApplicationEffects;
import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.presentation.FormCatalog;
import design.inherent.reform.presentation.FormLayout;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.reform.reference.ReferenceActions;
import design.inherent.reform.reference.ReferenceIndex;
import design.inherent.reform.reference.ReferenceLimits;
import design.inherent.reform.value.SnapshotPolicy;
import design.inherent.reform.value.ValueTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Thin native composition over public re:form files, forms and operation services. */
public final class StylePreferences {
  private static final Component TITLE = Component.literal("Lootr: Styled");
  private static final ApplicationEffects EFFECTS =
      ApplicationEffects.root(
          ApplicationEffects.Timing.EXPLICIT_RELOAD,
          "Save settings, then reload resources to update appearance.");
  private static final ReferenceLimits REFERENCES =
      new ReferenceLimits(4096, 1048576, 64, 256, 4096);
  private static final List<StyleWorkflow> workflows = new ArrayList<>();
  private static final ReferenceTitles profileTitles = new ReferenceTitles(Map.of());
  private static ClientFileSettings<ClientSettings> preferences;
  private static ReferenceActions<String, String> profiles;
  private static String profileRevision;
  private static OwnerContext context;
  private static StyleOperations operations;
  private static ClientRegistration.Subscription tick;

  private StylePreferences() {}

  public static void register(ClientRegistration registration) {
    preferences =
        ClientFileSettings.stagedFile(
            registration,
            ClientRuntime.settingsStore(),
            FileOwner.Initialization.CREATE_DEFAULTS,
            EFFECTS);
    registration.registerMenu(StylePreferences::screen);
    registration.registerCommands(
        "lootrstyled",
        new ClientRegistration.CommandTree() {
          @Override
          public <S> com.mojang.brigadier.builder.LiteralArgumentBuilder<S> bind(
              ClientRegistration.CommandSource<S> source) {
            return com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("lootrstyled")
                .executes(
                    command -> {
                      source.requireLocalInvocation(command.getSource());
                      if (!source.current(command.getSource())) return 0;
                      NativeUi.open(screen(NativeUi.current()));
                      return 1;
                    })
                .then(
                    preferences.commandBranch(
                        "settings",
                        source,
                        design.inherent.reform.minecraft.client.ClientCommands::chat));
          }
        });
    registration.onSetup(
        () -> {
          context =
              new OwnerContext(
                  Minecraft.getInstance()::execute,
                  ClientRuntime.workers(),
                  StylePreferences::requireClient,
                  OwnerContext.Limits.STANDARD);
          operations = new StyleOperations(context);
          tick =
              registration.onTick(
                  () -> {
                    refreshProfiles();
                    workflows.removeIf(
                        workflow -> {
                          if (workflow.isOpen()) return false;
                          workflow.close();
                          return true;
                        });
                  });
          preferences
              .initialization()
              .whenComplete(
                  (state, failed) ->
                      Minecraft.getInstance()
                          .execute(
                              () -> {
                                if (context != null
                                    && context.isOpen()
                                    && failed == null
                                    && state.lifecycle() == ClientFileSettings.Lifecycle.READY)
                                  operations.start(
                                      StyleOperations.Action.RELOAD,
                                      design.inherent.reform.operation.OperationDescriptor
                                          .EntryPoint.INTERNAL);
                              }));
        });
  }

  public static ClientFileSettings<ClientSettings> settings() {
    return preferences;
  }

  public static Screen screen(Screen parent) {
    requireClient();
    if (operations == null) return preferences.screen(parent, TITLE);
    return open(parent).screen();
  }

  /** Open a retained composition for the native menu, command or another client entry point. */
  public static design.inherent.reform.minecraft.client.ClientEditingWorkflow open(Screen parent) {
    requireClient();
    if (operations == null)
      throw new IllegalStateException("Client settings are still initializing");
    workflows.removeIf(
        workflow -> {
          if (workflow.isOpen()) return false;
          workflow.close();
          return true;
        });
    if (workflows.size() >= 16)
      throw new IllegalStateException("Close another Lootr: Styled editor before opening one");
    var workflow = new StyleWorkflow(parent, form(), operations, context);
    workflows.add(workflow);
    return workflow.workflow();
  }

  public static StyleApplication.Result resourceStatus() {
    requireClient();
    if (operations == null)
      return new StyleApplication.Result(
          StyleApplication.State.UNKNOWN,
          StyleApplication.Code.NOT_RELOADED,
          "Settings are still initializing",
          Optional.empty(),
          Optional.empty(),
          List.of());
    return operations.application().last();
  }

  private static ClientForm form() {
    return ClientForm.of(
        StyleSettingsStore.DEFINITION.id(),
        Component.literal("Appearance"),
        () -> {
          var lifecycle = preferences.status().lifecycle();
          return new ClientForm.Availability(
              lifecycle == ClientFileSettings.Lifecycle.READY,
              lifecycle == ClientFileSettings.Lifecycle.UNAVAILABLE,
              Component.literal(
                  lifecycle == ClientFileSettings.Lifecycle.READY
                      ? "Ready"
                      : "Settings unavailable"));
        },
        parent -> preferences.screen(parent, Component.literal("Appearance"), options()),
        Optional.of(() -> preferences.retryInitialization()));
  }

  private static ClientFormOptions<ClientSettings> options() {
    refreshProfiles();
    return new ClientFormOptions<ClientSettings>(
            presentation(), Map.of(StyleSettingsStore.PROFILES, profiles))
        .withDefaults(
            Component.literal("Defaults"),
            Component.literal("Load default appearance settings into this draft."))
        .withReferenceTitles(StyleSettingsStore.PROFILES, profileTitles)
        .withInformation(
            definition ->
                Optional.of(
                    FormInformation.of(
                        definition,
                        info ->
                            new FormInformation.Subject(
                                    Component.literal(help(info.path())),
                                    List.of(
                                        new FormInformation.Fact(
                                            Component.literal("Last reload"),
                                            Component.literal(
                                                operations.application().last().message()))),
                                    List.of(),
                                    Optional.of("settings.json"))
                                .withTechnicalDetails(
                                    FormInformation.TechnicalDetails.DISCLOSURE))));
  }

  private static FormPresentation presentation() {
    var root = design.inherent.reform.definition.FieldPath.ROOT;
    var enabled = root.field("enabled");
    var models = root.field("models");
    var artwork = root.field("artwork");
    var fresh = root.field("freshObjects");
    var catalog =
        new FormCatalog(
            List.of(
                new FormCatalog.Section(
                    enabled,
                    "lootr_styled.settings.enabled",
                    "Enabled",
                    "Turning this off keeps your selections."),
                new FormCatalog.Section(
                    models,
                    "lootr_styled.settings.models",
                    "Models",
                    "Container shapes, separate from artwork."),
                new FormCatalog.Section(
                    artwork,
                    "lootr_styled.settings.artwork",
                    "Artwork",
                    "Textures, separate from models."),
                new FormCatalog.Section(
                    fresh,
                    "lootr_styled.settings.fresh",
                    "Fresh Objects",
                    "Auto uses compatible Fresh Objects models.")));
    var layout =
        new FormLayout(
            List.of(new FormLayout.Row("profiles", List.of(models, artwork))),
            List.of(
                new FormLayout.Group(
                    "styling",
                    "lootr_styled.settings.styling",
                    "Styling",
                    "",
                    List.of(enabled, fresh)),
                new FormLayout.Group(
                    "selection",
                    "lootr_styled.settings.selection",
                    "Profile selection",
                    "",
                    List.of(models, artwork))));
    return FormPresentation.EMPTY.withCatalog(catalog).withLayout(layout);
  }

  private static String help(design.inherent.reform.definition.FieldPath path) {
    var root = design.inherent.reform.definition.FieldPath.ROOT;
    if (path.equals(root.field("enabled")))
      return "Enable styling. Turning it off keeps your profile selections.";
    if (path.equals(root.field("models")))
      return "Choose container shapes independently of artwork. Missing profiles keep their saved ID and use a native fallback.";
    if (path.equals(root.field("artwork")))
      return "Choose textures independently of models. Explicit Lootr resource-pack overrides keep precedence over Default artwork.";
    if (path.equals(root.field("freshObjects")))
      return "Auto uses supported Fresh Objects rigs when available. On reports missing support; Off keeps native lid motion.";
    return "Save settings, then reload resources to update appearance.";
  }

  static void refreshProfiles() {
    requireClient();
    if (preferences.status().lifecycle() != ClientFileSettings.Lifecycle.READY) return;
    if (profiles == null)
      profiles =
          preferences.references(
              StyleSettingsStore.PROFILES,
              ValueTypes.text(256),
              SnapshotPolicy.immutable(),
              REFERENCES);
    var active = NativeUi.activePack();
    var published =
        active.flatMap(GeneratedPack::preparation).filter(ClientRuntime.Preparation::qualified);
    String revision =
        active
            .filter(pack -> pack.preparation().isPresent())
            .map(pack -> pack.id().toString())
            .orElse("unavailable");
    if (revision.equals(profileRevision)) return;
    var entries =
        published.map(ClientRuntime.Preparation::choices).orElse(List.of()).stream()
            .map(StylePreferences::profileEntry)
            .toList();
    profiles.bind(revision, stamp -> profileSource(stamp, published.isPresent(), entries));
    profileTitles.replace(
        entries.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    ReferenceIndex.Entry::key, entry -> Component.literal(entry.label()))));
    profileRevision = revision;
  }

  static ReferenceIndex.Entry<String, String> profileEntry(
      design.inherent.lootr_styled.resources.PresentationCompiler.Choice choice) {
    var facts = new java.util.LinkedHashMap<String, String>();
    facts.put("ID", choice.id().toString());
    facts.put("Source", choice.source());
    choice
        .declarations()
        .ifPresent(
            counts -> {
              facts.put("Declared model entries", Integer.toString(counts.models()));
              facts.put("Declared artwork entries", Integer.toString(counts.artwork()));
            });
    return new ReferenceIndex.Entry<>(
        choice.id().toString(),
        choice.id().toString(),
        choice.title(),
        choice.id() + " " + choice.title() + " " + choice.source(),
        facts);
  }

  static design.inherent.reform.reference.ReferenceResolver<String, String> profileSource(
      design.inherent.reform.reference.ReferenceStamp stamp,
      boolean available,
      List<ReferenceIndex.Entry<String, String>> entries) {
    if (available)
      return new ReferenceIndex<>(
          stamp, ValueTypes.text(256), SnapshotPolicy.immutable(), REFERENCES, entries);
    return new design.inherent.reform.reference.ReferenceResolver<>() {
      @Override
      public design.inherent.reform.reference.ReferenceStamp stamp() {
        return stamp;
      }

      @Override
      public design.inherent.reform.reference.ReferenceResolution<String> resolve(String key) {
        return design.inherent.reform.reference.ReferenceResolution.unavailable(
            stamp,
            design.inherent.reform.reference.ReferenceResolution.Status.UNAVAILABLE_CONTEXT,
            "Profiles are unavailable; finish reloading resources or check resource details");
      }

      @Override
      public design.inherent.reform.reference.ReferencePage<String> search(
          design.inherent.reform.reference.ReferenceQuery query) {
        return design.inherent.reform.reference.ReferencePage.unavailable(
            stamp,
            design.inherent.reform.reference.ReferencePage.Availability.UNAVAILABLE_CONTEXT,
            "Profiles are unavailable; finish reloading resources or check resource details");
      }
    };
  }

  static void requireClient() {
    if (!Minecraft.getInstance().isSameThread())
      throw new IllegalStateException("Client thread required");
  }

  public static void close() {
    requireClient();
    workflows.forEach(StyleWorkflow::close);
    workflows.clear();
    if (tick != null) tick.close();
    if (context != null) context.close();
    ClientRuntime.close();
  }
}
