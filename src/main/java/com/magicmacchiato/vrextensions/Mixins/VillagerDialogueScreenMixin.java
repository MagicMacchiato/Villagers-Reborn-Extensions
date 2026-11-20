package com.magicmacchiato.vrextensions.Mixins;

import com.javic.slimpatch.client.gui.VillagerDialogueScreen;
import com.magicmacchiato.vrextensions.Dialogue.DialogueNode;
import com.magicmacchiato.vrextensions.Utils.DialogueManager;
import com.magicmacchiato.vrextensions.Features.DialogueReplyReceiver;
import com.magicmacchiato.vrextensions.Network.ChooseDialoguePacket;
import com.magicmacchiato.vrextensions.Network.StartConversationPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;

/** Replaces the Friendly/Mean/Joke/Flirt buttons with player dialogue nodes loaded from JSON. */
@Mixin(value = VillagerDialogueScreen.class, remap = false)
public abstract class VillagerDialogueScreenMixin implements DialogueReplyReceiver {

    /** Player choices after the villager's current line; set by the server's DialogueReplyPacket. */
    @Unique private List<DialogueNode> vrextensions$choices;
    @Unique private boolean vrextensions$started;

    @Shadow @Final private Villager villager;
    @Shadow public abstract void showDialogueResult(int entityId, String option, boolean success, String line);
    @Shadow private void rebuildMenuWidgets() {}

    @Inject(method = "getCurrentMenuOptions", at = @At("RETURN"), cancellable = true)
    private void vrextensions$replaceSocialOptions(CallbackInfoReturnable<List<String>> cir) {
        // The social menu is the only one containing the vanilla "Friendly" option.
        if (!cir.getReturnValue().contains("Friendly")) return;

        // No conversation (yet, or any more): keep the original menu.
        if (vrextensions$choices == null || vrextensions$choices.isEmpty()) return;

        List<String> options = new ArrayList<>();
        for (DialogueNode node : vrextensions$choices) options.add(node.text());
        cir.setReturnValue(options);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void vrextensions$startConversation(CallbackInfo ci) {
        if (vrextensions$started) return; // init() also runs on window resize
        vrextensions$started = true;
        PacketDistributor.sendToServer(new StartConversationPacket(villager.getId()));
    }

    @Inject(method = "onOptionClicked", at = @At("HEAD"), cancellable = true)
    private void vrextensions$onChoiceClicked(String option, CallbackInfo ci) {
        if (vrextensions$choices == null) return;
        for (DialogueNode node : vrextensions$choices) {
            if (node.text().equals(option)) {
                // Don't send the base mod's RelationshipPacket (it would grant hearts for an unknown option).
                vrextensions$handleChoice(node);
                ci.cancel();
                return;
            }
        }
    }

    @Unique
    private void vrextensions$handleChoice(DialogueNode node) {
        // The server applies the mood change and picks the reply; it answers with a DialogueReplyPacket.
        PacketDistributor.sendToServer(new ChooseDialoguePacket(villager.getId(), node.id()));
    }

    @Override
    public void onVrReply(int entityId, String nodeId, List<String> choiceIds) {
        if (villager.getId() != entityId) return;
        DialogueNode node = DialogueManager.get(nodeId);
        if (node != null) {
            showDialogueResult(entityId, "", true, node.text());
            vrextensions$choices = new ArrayList<>();
            for (String id : choiceIds) {
                DialogueNode choice = DialogueManager.get(id);
                if (choice != null) vrextensions$choices.add(choice);
            }
        } else {
            vrextensions$choices = List.of();
        }
        rebuildMenuWidgets();
    }
}
