package com.magicmacchiato.vrextensions.Features;

/** Implemented (via mixin) by the villager dialogue screen so packets can deliver the villager's mood. */
public interface MoodReceiver {
    void onVrMood(int entityId, float positiveness, float eagerness, float security);
}
