package com.magicmacchiato.vrextensions.Mixins;

import com.javic.slimpatch.client.gui.VillagerDialogueScreen;
import com.magicmacchiato.vrextensions.Dialogue.Dialogue;
import com.magicmacchiato.vrextensions.Enums.Emotion;
import com.magicmacchiato.vrextensions.Utils.DialogueManager;
import com.magicmacchiato.vrextensions.Features.DialogueReplyReceiver;
import com.magicmacchiato.vrextensions.Features.MoodReceiver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
public abstract class VillagerDialogueScreenMixin implements DialogueReplyReceiver, MoodReceiver {

    /** Player choices after the villager's current line; set by the server's DialogueReplyPacket. */
    @Unique private List<Dialogue> vrextensions$choices;
    @Unique private boolean vrextensions$started;

    @Unique private boolean vrextensions$hasMood;
    @Unique private String vrextensions$emotionText = "";
    @Unique private int vrextensions$emotionColor = 0xFFFFFFFF;

    @Shadow @Final private Villager villager;
    @Shadow private String getDisplayedVillagerName(String name) { return name; }
    @Shadow public abstract void showDialogueResult(int entityId, String option, boolean success, String line);
    @Shadow private void rebuildMenuWidgets() {}

    @Inject(method = "getCurrentMenuOptions", at = @At("RETURN"), cancellable = true)
    private void vrextensions$replaceSocialOptions(CallbackInfoReturnable<List<String>> cir) {
        // The social menu is the only one containing the vanilla "Friendly" option.
        if (!cir.getReturnValue().contains("Friendly")) return;

        // No conversation (yet, or any more): keep the original menu.
        if (vrextensions$choices == null || vrextensions$choices.isEmpty()) return;

        List<String> options = new ArrayList<>();
        for (Dialogue node : vrextensions$choices) options.add(node.text());
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
        for (Dialogue node : vrextensions$choices) {
            if (node.text().equals(option)) {
                // Don't send the base mod's RelationshipPacket (it would grant hearts for an unknown option).
                vrextensions$handleChoice(node);
                ci.cancel();
                return;
            }
        }
    }

    @Unique
    private void vrextensions$handleChoice(Dialogue node) {
        // The server applies the mood change and picks the reply; it answers with a DialogueReplyPacket.
        PacketDistributor.sendToServer(new ChooseDialoguePacket(villager.getId(), node.id()));
    }

    @Override
    public void onVrReply(int entityId, String nodeId, List<String> choiceIds) {
        if (villager.getId() != entityId) return;
        Dialogue node = DialogueManager.get(nodeId);
        if (node != null) {
            showDialogueResult(entityId, "", true, node.text());
            vrextensions$choices = new ArrayList<>();
            for (String id : choiceIds) {
                Dialogue choice = DialogueManager.get(id);
                if (choice != null) vrextensions$choices.add(choice);
            }
        } else {
            vrextensions$choices = List.of();
        }
        rebuildMenuWidgets();
    }

    @Override
    public void onVrMood(int entityId, float positiveness, float eagerness, float security) {
        if (villager.getId() != entityId) return;
        vrextensions$hasMood = true;
        // Recompute the badge only when the mood changes, not every frame.
        vrextensions$emotionText = Emotion.fromMood(positiveness, eagerness, security).toString();
        vrextensions$emotionColor = 0xFF000000
                | Integer.parseInt(Emotion.getColor(positiveness, eagerness, security).substring(1), 16);
    }

    /** Mood badge (emotion name in its mood color) right of the villager's name (the name is drawn at x=10, y=34 by the base screen). */
    @Inject(method = "render", at = @At("TAIL"))
    private void vrextensions$renderMoodBadge(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!vrextensions$hasMood) return;
        Font font = Minecraft.getInstance().font;
        String name = villager.hasCustomName() ? villager.getCustomName().getString() : villager.getName().getString();
        String text = vrextensions$emotionText;
        int color = vrextensions$emotionColor;

        int padX = 4, padY = 2;
        int x = 10 + font.width(getDisplayedVillagerName(name)) + 8;
        int y = 34 - padY;
        int w = font.width(text) + padX * 2, h = font.lineHeight + padY * 2 - 1;
        graphics.fill(x, y, x + w, y + h, 0xAA000000);             // background
        graphics.renderOutline(x, y, w, h, 0xFF8A8A8A);            // border
        graphics.drawString(font, text, x + padX, y + padY, color);
    }
}
