package com.magicmacchiato.vrextensions.Utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side state: which villager node each villager is currently "saying" to which player. */
public class ConversationManager {
    public record State(UUID player, String villagerNodeId) {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public static void set(UUID villager, UUID player, String villagerNodeId) {
        STATES.put(villager, new State(player, villagerNodeId));
    }

    public static State get(UUID villager) {
        return STATES.get(villager);
    }

    public static void end(UUID villager) {
        STATES.remove(villager);
    }
}
