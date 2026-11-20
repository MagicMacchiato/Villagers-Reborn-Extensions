package com.magicmacchiato.vrextensions.Network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {
    public static final String PROTOCOL = "1";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(StartConversationPacket.TYPE, StartConversationPacket.CODEC, StartConversationPacket::handle);
        registrar.playToServer(ChooseDialoguePacket.TYPE, ChooseDialoguePacket.CODEC, ChooseDialoguePacket::handle);
        registrar.playToClient(DialogueReplyPacket.TYPE, DialogueReplyPacket.CODEC, DialogueReplyPacket::handle);
    }
}
