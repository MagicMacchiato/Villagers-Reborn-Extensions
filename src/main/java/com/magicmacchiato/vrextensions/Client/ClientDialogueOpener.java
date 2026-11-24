package com.magicmacchiato.vrextensions.Client;

import com.javic.slimpatch.client.gui.VillagerDialogueScreen;
import com.javic.slimpatch.dialogue.DialogueManager;
import com.javic.slimpatch.network.DialogueStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of a villager starting a conversation with the player. Does what Villagers Reborn's "open dialogue" key
 * does when the player looks at a villager: marks the dialogue active (client and server) and opens the screen. Kept
 * in its own class so the dedicated server never loads the client-only screen.
 */
public final class ClientDialogueOpener {
    private ClientDialogueOpener() {}

    public static void open(int villagerId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // do not interrupt another screen (inventory, chest, ...)
        if (!(mc.level.getEntity(villagerId) instanceof Villager villager)) return;

        DialogueManager.startDialogue(villager, mc.player);
        PacketDistributor.sendToServer(new DialogueStatePacket(villager.getId(), true));
        mc.setScreen(new VillagerDialogueScreen(villager));
    }
}
