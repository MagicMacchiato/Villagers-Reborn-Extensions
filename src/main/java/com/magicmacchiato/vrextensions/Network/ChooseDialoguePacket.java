package com.magicmacchiato.vrextensions.Network;

import com.javic.slimpatch.dialogue.DialogueManager;
import com.javic.slimpatch.entity.FemaleVillagerEntity;
import com.javic.slimpatch.entity.MaleVillagerEntity;
import com.javic.slimpatch.entity.VillagerPersonality;
import com.magicmacchiato.vrextensions.Dialogue.DialogueNode;
import com.magicmacchiato.vrextensions.Enums.Speaker;
import com.magicmacchiato.vrextensions.Features.Mood;
import com.magicmacchiato.vrextensions.Features.MoodHolder;
import com.magicmacchiato.vrextensions.Features.MoodSync;
import com.magicmacchiato.vrextensions.Utils.ConversationManager;
import com.magicmacchiato.vrextensions.Utils.Requirements;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;
import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

/** Client -> server: the player picked a dialogue node while talking to a villager. */
public record ChooseDialoguePacket(int entityId, String nodeId) implements CustomPacketPayload {
    public static final Type<ChooseDialoguePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "choose_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseDialoguePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseDialoguePacket::entityId,
            ByteBufCodecs.STRING_UTF8, ChooseDialoguePacket::nodeId,
            ChooseDialoguePacket::new);

    private static final double MAX_DISTANCE_SQR = 64.0D;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChooseDialoguePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.serverLevel().getEntity(msg.entityId) instanceof Villager villager)) return;
            if (player.distanceToSqr(villager) > MAX_DISTANCE_SQR) return;
            // Only the player currently in dialogue with this villager may choose.
            if (!player.getUUID().equals(DialogueManager.getDialoguePlayer(villager))) return;

            // The choice must be one of the options after the villager's current line.
            ConversationManager.State state = ConversationManager.get(villager.getUUID());
            if (state == null || !player.getUUID().equals(state.player())) return;
            DialogueNode current = com.magicmacchiato.vrextensions.Utils.DialogueManager.get(state.villagerNodeId());
            DialogueNode node = com.magicmacchiato.vrextensions.Utils.DialogueManager.get(msg.nodeId);
            if (current == null || node == null || node.speaker() != Speaker.player
                    || !current.nextChoice().contains(node.id())) return;
            if (!Requirements.forPair(villager, player).test(node)) return;

            VillagerPersonality personality = villager instanceof MaleVillagerEntity male ? male.getPersonality()
                    : villager instanceof FemaleVillagerEntity female ? female.getPersonality() : null;
            if (personality != null && villager instanceof MoodHolder holder) {
                float reaction = Mood.reaction(personality, node.semantic());
                holder.getVrMood().applyReaction(reaction);
                holder.getVrMood().logState(villager.getUUID());
                MoodSync.send(player, villager);
            }

            // Mood has just changed, so evaluate the reply's requirements afterwards.
            var allowed = Requirements.forPair(villager, player);
            DialogueNode reply = com.magicmacchiato.vrextensions.Utils.DialogueManager.randomReply(node, allowed);
            List<String> choiceIds = reply == null ? List.of()
                    : com.magicmacchiato.vrextensions.Utils.DialogueManager.choicesOf(reply, allowed).stream().map(DialogueNode::id).toList();
            if (!choiceIds.isEmpty()) {
                ConversationManager.set(villager.getUUID(), player.getUUID(), reply.id());
            } else {
                ConversationManager.end(villager.getUUID()); // the conversation is over
            }
            PacketDistributor.sendToPlayer(player, new DialogueReplyPacket(villager.getId(), reply == null ? "" : reply.id(), choiceIds));
        });
    }
}
