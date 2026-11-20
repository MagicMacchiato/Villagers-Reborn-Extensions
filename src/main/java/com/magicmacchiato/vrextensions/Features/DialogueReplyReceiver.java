package com.magicmacchiato.vrextensions.Features;

/** Implemented (via mixin) by the villager dialogue screen so packets can deliver the villager's reply. */
public interface DialogueReplyReceiver {
    /** @param replyNodeId id of the villager's reply node, or "" if the villager has nothing to say */
    void onVrReply(int entityId, String replyNodeId, java.util.List<String> choiceIds);
}
