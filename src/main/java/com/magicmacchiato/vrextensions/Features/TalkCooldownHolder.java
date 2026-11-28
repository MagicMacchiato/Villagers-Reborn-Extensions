package com.magicmacchiato.vrextensions.Features;

/**
 * Implemented (via mixin) by villager entities: the cooldown and the per-day count of conversations. Until the stored game time the villager neither starts a conversation
 * nor can be picked by another villager. Saved with the villager; game time carries over with the world.
 */
public interface TalkCooldownHolder {
    long getVrNextTalk();

    void setVrNextTalk(long gameTime);

    /** Conversations counted on this day, 0 if the stored count belongs to another day. */
    int getVrTalks(long day);

    /** Counts one conversation on this day. */
    void addVrTalk(long day);
}
