package io.github.andrewwwwwwwwwwwwwww.fallen.api;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public entry point for Fallen compatibility addons.
 *
 * <p>Register a {@link CorpseSlotProvider} during mod init to have items from
 * your own slots stored in the corpse alongside the player's inventory:
 *
 * <pre>{@code
 * FallenApi.register(Identifier.fromNamespaceAndPath("mymod", "backpack"), new MyProvider());
 * }</pre>
 *
 * <p>Ordering is stable: providers are captured and restored in registration
 * order, so each one gets its own stacks back.
 */
public final class FallenApi {
    private FallenApi() {}

    private static final Map<Identifier, CorpseSlotProvider> PROVIDERS = new LinkedHashMap<>();

    /** Register a provider. Registering the same id twice replaces the first. */
    public static synchronized void register(Identifier id, CorpseSlotProvider provider) {
        if (id == null || provider == null) {
            throw new IllegalArgumentException("Fallen: slot provider id and provider must not be null");
        }
        if (PROVIDERS.put(id, provider) != null) {
            Fallen.LOGGER.warn("[Fallen] slot provider '{}' was registered twice; using the latest", id);
        } else {
            Fallen.LOGGER.info("[Fallen] registered slot provider '{}'", id);
        }
    }

    public static synchronized boolean hasProviders() {
        return !PROVIDERS.isEmpty();
    }

    /**
     * Total display slots reserved by every registered provider. Drives how many
     * read-only "stored equipment" slots the corpse screen shows. Because
     * addons load on both sides, this is identical on client and server, so the
     * screen layouts match. Zero when no addon reserves any (the default).
     */
    public static synchronized int totalDisplaySlots() {
        int total = 0;
        for (CorpseSlotProvider provider : PROVIDERS.values()) {
            total += Math.max(0, provider.displaySlots());
        }
        return total;
    }

    /** Flatten captured groups into one ordered list of stacks (copies), for display. */
    public static List<ItemStack> flatten(List<Group> groups) {
        List<ItemStack> out = new ArrayList<>();
        for (Group group : groups) {
            for (ItemStack stack : group.stacks()) {
                if (stack != null && !stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
        }
        return out;
    }

    /**
     * Capture from every provider, in registration order. Each provider's
     * stacks are kept in their own group so they can be handed back to the
     * right provider later.
     */
    public static synchronized List<Group> captureAll(Player player) {
        List<Group> groups = new ArrayList<>();
        PROVIDERS.forEach((id, provider) -> {
            try {
                List<ItemStack> taken = provider.capture(player);
                if (taken != null && !taken.isEmpty()) {
                    List<ItemStack> copy = new ArrayList<>();
                    for (ItemStack stack : taken) {
                        if (stack != null && !stack.isEmpty()) {
                            copy.add(stack);
                        }
                    }
                    if (!copy.isEmpty()) {
                        groups.add(new Group(id, copy));
                    }
                }
            } catch (Exception e) {
                // A broken addon must never destroy someone's inventory.
                Fallen.LOGGER.error("[Fallen] slot provider '{}' threw while capturing; skipping it", id, e);
            }
        });
        return groups;
    }

    /**
     * Hand each group back to the provider that captured it.
     *
     * @return stacks that no provider could place back
     */
    public static synchronized List<ItemStack> restoreAll(Player player, List<Group> groups) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (Group group : groups) {
            CorpseSlotProvider provider = PROVIDERS.get(group.providerId());
            if (provider == null) {
                // The addon is no longer installed — don't lose the items.
                leftovers.addAll(group.stacks());
                continue;
            }
            try {
                List<ItemStack> rejected = provider.restore(player, group.stacks());
                if (rejected != null) {
                    leftovers.addAll(rejected);
                }
            } catch (Exception e) {
                Fallen.LOGGER.error("[Fallen] slot provider '{}' threw while restoring; returning its items to the player",
                        group.providerId(), e);
                leftovers.addAll(group.stacks());
            }
        }
        return leftovers;
    }

    /** One provider's captured stacks, tagged with which provider they came from. */
    public record Group(Identifier providerId, List<ItemStack> stacks) {
        public static final com.mojang.serialization.Codec<Group> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("provider").forGetter(Group::providerId),
                        ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Group::stacks)
                ).apply(instance, Group::new));
    }
}
