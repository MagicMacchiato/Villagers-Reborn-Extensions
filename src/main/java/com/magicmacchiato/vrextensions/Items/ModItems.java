package com.magicmacchiato.vrextensions.Items;

import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

public class ModItems {
    /** The mod's single Registrate instance; every game object is registered through it. */
    public static final Registrate REGISTRATE = Registrate.create(MOD_ID);

    /**
     * Debug tool: right-click a villager to dump its relationships into chat. See {@link RelationshipDumpEvents}.
     * Deliberately not in any creative tab; get it with {@code /give @s vrextensions:relationship_dumper}.
     */
    public static final ItemEntry<Item> RELATIONSHIP_DUMPER = REGISTRATE
            .item("relationship_dumper", Item::new)
            .properties(p -> p.stacksTo(1))
            .lang("Relationship Dumper")
            .model((ctx, prov) -> prov.generated(ctx, ResourceLocation.withDefaultNamespace("item/writable_book")))
            .register();

    /**
     * Call from the mod constructor to load this class, which creates the Registrate instance and queues the items.
     * Do NOT call {@code registerEventListeners} here: {@code Registrate.create} already hooks the mod event bus, and a
     * second hook registers every entry twice ("Missing intrusive holder" assertion).
     */
    public static void init() {
    }
}
