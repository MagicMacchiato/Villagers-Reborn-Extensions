package com.magicmacchiato.vrextensions.Network;

import com.magicmacchiato.vrextensions.Features.MoodReceiver;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

/** Server -> client: the current mood of a villager, shown in the dialogue screen. */
public record MoodSyncPacket(int entityId, float positiveness, float eagerness, float security) implements CustomPacketPayload {
    public static final Type<MoodSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "mood_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoodSyncPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MoodSyncPacket::entityId,
            ByteBufCodecs.FLOAT, MoodSyncPacket::positiveness,
            ByteBufCodecs.FLOAT, MoodSyncPacket::eagerness,
            ByteBufCodecs.FLOAT, MoodSyncPacket::security,
            MoodSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MoodSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof MoodReceiver receiver) {
                receiver.onVrMood(msg.entityId, msg.positiveness, msg.eagerness, msg.security);
            }
        });
    }
}
