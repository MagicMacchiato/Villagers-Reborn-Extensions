package com.magicmacchiato.vrextensions.Items;

import com.magicmacchiato.vrextensions.Features.RelationshipHolder;
import com.magicmacchiato.vrextensions.Features.RelationshipStatusData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Right-click a villager with the relationship dumper to print, into the player's chat, what it thinks of others and
 * what other nearby villagers think of it. Hooked on the interact event (before the villager's own interaction) and
 * cancelled on both sides, so the dialogue / trading screen does not open while holding the item.
 */
public class RelationshipDumpEvents {
    /** How far around the target to look for villagers whose relationships point at it. */
    private static final double INCOMING_RADIUS = 64.0D;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getItemStack().is(ModItems.RELATIONSHIP_DUMPER.get())) return;

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        if (event.getTarget() instanceof Villager villager && villager instanceof RelationshipHolder holder) {
            dump(player, level, villager, holder);
        } else {
            player.sendSystemMessage(Component.literal("Not a villager with relationships."));
        }
    }

    private static void dump(ServerPlayer player, ServerLevel level, Villager villager, RelationshipHolder holder) {
        UUID id = villager.getUUID();
        player.sendSystemMessage(Component.literal("== Relationships of " + villager.getName().getString()
                + " (" + id + ") =="));

        Map<UUID, RelationshipStatusData> outgoing = holder.getVrRelationship().all();
        player.sendSystemMessage(Component.literal("Thinks of others (" + outgoing.size() + "):"));
        outgoing.forEach((target, data) ->
                player.sendSystemMessage(Component.literal(" - " + nameOf(level, target) + ": " + describe(data))));

        List<String> incoming = new ArrayList<>();
        for (Villager other : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(INCOMING_RADIUS))) {
            if (other == villager || !(other instanceof RelationshipHolder otherHolder)) continue;
            RelationshipStatusData data = otherHolder.getVrRelationship().getData(id);
            if (data != null) incoming.add(" - " + other.getName().getString() + " (" + shortId(other.getUUID())
                    + "): " + describe(data));
        }
        player.sendSystemMessage(Component.literal("Thought of by loaded villagers within "
                + (int) INCOMING_RADIUS + " blocks (" + incoming.size() + "):"));
        incoming.forEach(line -> player.sendSystemMessage(Component.literal(line)));
    }

    private static String describe(RelationshipStatusData data) {
        StringBuilder sb = new StringBuilder(data.status().toString()).append(", ")
                .append(data.getHearts()).append(" hearts");
        if (data.isFamily()) sb.append(", family");
        if (data.isPartner()) sb.append(", partner");
        if (data.isDating()) sb.append(", dating");
        return sb.toString();
    }

    /** Player or loaded entity name if it can be found, otherwise just the short id. */
    private static String nameOf(ServerLevel level, UUID id) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        Entity entity = player != null ? player : level.getEntity(id);
        return entity != null ? entity.getName().getString() + " (" + shortId(id) + ")" : "unloaded (" + shortId(id) + ")";
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
