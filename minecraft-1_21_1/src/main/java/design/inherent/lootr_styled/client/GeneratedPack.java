package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.ResourceGeneration;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

/** One owned layer per native reload. Lower packs are borrowed and closed only by Minecraft. */
public final class GeneratedPack implements PackResources {
  private static final PackLocationInfo LOCATION =
      new PackLocationInfo(
          "lootr_styled/generated",
          Component.literal("Lootr: Styled"),
          PackSource.DEFAULT,
          Optional.empty());
  private final ResourceGeneration generation;
  private final java.util.UUID id = java.util.UUID.randomUUID();
  private final java.util.concurrent.atomic.AtomicBoolean closed =
      new java.util.concurrent.atomic.AtomicBoolean();
  private final java.util.concurrent.CompletableFuture<ClientRuntime.Preparation> prepared;

  private GeneratedPack(List<PackResources> lower) {
    var sources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, lower);
    prepared = ClientRuntime.prepareAsync(new NativeSources(sources), () -> !closed.get());
    generation = new ResourceGeneration(() -> prepared.join().resources());
  }

  public java.util.UUID id() {
    return id;
  }

  public Optional<ClientRuntime.Preparation> preparation() {
    return generation.state() == ResourceGeneration.State.READY
        ? Optional.ofNullable(prepared.getNow(null))
        : Optional.empty();
  }

  public static List<PackResources> append(List<PackResources> packs) {
    var lower = packs.stream().filter(pack -> !(pack instanceof GeneratedPack)).toList();
    var result = new java.util.ArrayList<>(lower);
    result.add(new GeneratedPack(lower));
    return List.copyOf(result);
  }

  @Override
  public PackLocationInfo location() {
    return LOCATION;
  }

  @Override
  public IoSupplier<InputStream> getRootResource(String... paths) {
    return null;
  }

  @Override
  public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
    return null;
  }

  @Override
  public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
    if (type != PackType.CLIENT_RESOURCES) return null;
    var snapshot = generation.snapshot();
    if (snapshot.isEmpty()) return null;
    var current = snapshot.orElseThrow();
    var key = ResourceId.parse(id.toString());
    if (!generation.isCurrent(current) || !current.resources().ids().contains(key)) return null;
    return () -> {
      if (!generation.isCurrent(current)) throw new IOException("Retired Lootr: Styled generation");
      return current
          .resources()
          .open(key)
          .orElseThrow(() -> new IOException("Missing generated resource"));
    };
  }

  @Override
  public void listResources(
      PackType type, String namespace, String directory, ResourceOutput output) {
    if (type != PackType.CLIENT_RESOURCES) return;
    generation
        .snapshot()
        .ifPresent(
            snapshot -> {
              if (!generation.isCurrent(snapshot)) return;
              for (var id : snapshot.resources().ids()) {
                if (id.namespace().equals(namespace) && id.path().startsWith(directory + "/")) {
                  var nativeId = ResourceLocation.parse(id.toString());
                  var supplier = getResource(type, nativeId);
                  if (supplier != null) output.accept(nativeId, supplier);
                }
              }
            });
  }

  @Override
  public Set<String> getNamespaces(PackType type) {
    if (type != PackType.CLIENT_RESOURCES) return Set.of();
    return generation
        .snapshot()
        .filter(generation::isCurrent)
        .map(
            snapshot ->
                snapshot.resources().ids().stream()
                    .map(ResourceId::namespace)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()))
        .orElse(Set.of());
  }

  @Override
  public void close() {
    closed.set(true);
    generation.close();
    prepared.obtrudeValue(ClientRuntime.Preparation.EMPTY);
  }
}
