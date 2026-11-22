package com.magicmacchiato.vrextensions.Enums;

import com.magicmacchiato.vrextensions.Features.Mood;

public enum Emotion {
    EXCITED,
    THRILLED,
    RELAXED,
    CURIOUS,
    ANGRY,
    SCARED,
    SAD,
    HOPELESS,
    NEUTRAL;

    /**
     * Maps (positiveness, eagerness, security) to an emotion: a value is "+" when {@code > 0} and "-" when
     * {@code <= 0} (zero counts as "-"). Any NaN falls through to {@link #NEUTRAL}.
     */
    public static Emotion fromMood(float positiveness, float eagerness, float security) {
        if (Float.isNaN(positiveness) || Float.isNaN(eagerness) || Float.isNaN(security)) return NEUTRAL;
        boolean p = positiveness > 0f;
        boolean e = eagerness > 0f;
        boolean s = security > 0f;
        if (p && e && s) return EXCITED;
        else if (p && e) return THRILLED;
        else if (p && s) return RELAXED;
        else if (p) return CURIOUS;
        else if (e && s) return ANGRY;
        else if (e) return SCARED;
        else if (s) return SAD;
        else return HOPELESS;
    }

    /** Display name, e.g. {@code "Excited"}. Use {@link #name()} for the constant's identifier. */
    @Override
    public String toString() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * Badge color as {@code "#RRGGBB"}: eagerness -> red, positiveness -> green, security -> blue.
     * Each axis is clamped to +-{@link Mood#MOOD_LIMIT}, shifted by +5 into 0..10, then mapped with
     * {@code 0.081 * m + 0.09} into 0.09..0.9 and scaled to a byte (0..255). NaN counts as 0.
     */
    public static String getColor(float positiveness, float eagerness, float security) {
        return String.format("#%02X%02X%02X",
                colorChannel(eagerness), colorChannel(positiveness), colorChannel(security));
    }

    private static int colorChannel(float mood) {
        if (Float.isNaN(mood)) mood = 0f;
        float shifted = Math.max(-Mood.MOOD_LIMIT, Math.min(Mood.MOOD_LIMIT, mood)) + Mood.MOOD_LIMIT;
        float fraction = 0.081f * shifted + 0.09f;
        return Math.round(fraction * 255f);
    }
}
