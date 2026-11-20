package com.magicmacchiato.vrextensions.Network;

import com.magicmacchiato.vrextensions.Features.DialogueReplyReceiver;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

/** Server -> client: the villager node to say next (opener or reply); "" = nothing more to say.
 * choiceIds are the player nodes currently allowed by their requirements. */
public record DialogueReplyPacket(int entityId, String replyNodeId, List<String> choiceIds) implements CustomPacketPayload {
    public static final Type<DialogueReplyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "dialogue_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueReplyPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogueReplyPacket::entityId,
            ByteBufCodecs.STRING_UTF8, DialogueReplyPacket::replyNodeId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DialogueReplyPacket::choiceIds,
            DialogueReplyPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DialogueReplyPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof DialogueReplyReceiver receiver) {
                receiver.onVrReply(msg.entityId, msg.replyNodeId, msg.choiceIds);
            }
        });
    }
}
