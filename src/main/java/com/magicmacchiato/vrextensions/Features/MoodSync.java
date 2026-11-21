package com.magicmacchiato.vrextensions.Features;

import com.magicmacchiato.vrextensions.Network.MoodSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helpers that push a villager's mood to the player talking to it. */
public final class MoodSync {
    private MoodSync() {}

    public static void send(ServerPlayer player, Villager villager) {
        if (!(villager instanceof MoodHolder holder)) return;
        Mood m = holder.getVrMood();
        PacketDistributor.sendToPlayer(player, new MoodSyncPacket(villager.getId(), m.getPositiveness(), m.getEagerness(), m.getSecurity()));
    }

    /** Sends the mood to the player currently in dialogue with this villager, if any. */
    public static void sendToDialoguePlayer(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        var playerId = com.javic.slimpatch.dialogue.DialogueManager.getDialoguePlayer(villager);
        if (playerId == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) send(player, villager);
    }
}
