package com.magicmacchiato.vrextensions.Network;

import com.javic.slimpatch.dialogue.DialogueManager;
import com.magicmacchiato.vrextensions.Dialogue.DialogueNode;
import com.magicmacchiato.vrextensions.Utils.ConversationManager;
import com.magicmacchiato.vrextensions.Utils.Requirements;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

/** Client -> server: the dialogue screen opened; the villager should start talking. */
public record StartConversationPacket(int entityId) implements CustomPacketPayload {
    public static final Type<StartConversationPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "start_conversation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StartConversationPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StartConversationPacket::entityId,
            StartConversationPacket::new);

    private static final double MAX_DISTANCE_SQR = 64.0D;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StartConversationPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.serverLevel().getEntity(msg.entityId) instanceof Villager villager)) return;
            if (player.distanceToSqr(villager) > MAX_DISTANCE_SQR) return;
            if (!player.getUUID().equals(DialogueManager.getDialoguePlayer(villager))) return;

            var allowed = Requirements.forPair(villager, player);
            DialogueNode opener = com.magicmacchiato.vrextensions.Utils.DialogueManager.randomOpener(allowed);
            if (opener == null) return;
            ConversationManager.set(villager.getUUID(), player.getUUID(), opener.id());
            PacketDistributor.sendToPlayer(player, new DialogueReplyPacket(villager.getId(), opener.id(),
                    com.magicmacchiato.vrextensions.Utils.DialogueManager.choicesOf(opener, allowed).stream().map(DialogueNode::id).toList()));
        });
    }
}
