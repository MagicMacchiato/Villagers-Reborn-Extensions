package com.magicmacchiato.vrextensions.Network;

import com.magicmacchiato.vrextensions.Client.ClientDialogueOpener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

/** Server -> client: a villager walked up to the player and wants to talk; open its dialogue screen. */
public record OpenDialoguePacket(int entityId) implements CustomPacketPayload {
    public static final Type<OpenDialoguePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "open_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenDialoguePacket::entityId,
            OpenDialoguePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDialoguePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientDialogueOpener.open(msg.entityId));
    }
}
