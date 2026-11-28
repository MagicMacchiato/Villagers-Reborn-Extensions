package com.magicmacchiato.vrextensions.Dialogue;

import com.google.gson.annotations.SerializedName;
import com.magicmacchiato.vrextensions.Enums.RelationshipStatus;
import com.magicmacchiato.vrextensions.Features.Mood;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What must hold for a dialogue line to be allowed. An empty {@code stage} list means any status; a missing
 * {@code min_mood} means no mood gate.
 */
public record Requirement(
        @SerializedName("min_heart") int minHeart,
        @SerializedName("stage") List<RelationshipStatus> stages,
        @SerializedName("min_mood") @Nullable MinMood minMood
) {
    /** A requirement that every speaker/listener pair meets. */
    public static final Requirement NONE = new Requirement(0, null, null);

    public Requirement {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /** True if the hearts (the speaker's view of the listener), status and the speaker's mood meet every gate. */
    public boolean isMetBy(int hearts, RelationshipStatus status, Mood mood) {
        if (hearts < minHeart) return false;
        if (!stages.isEmpty() && !stages.contains(status)) return false;
        if (minMood != null) {
            if (minMood.positiveness() != null && mood.getPositiveness() < minMood.positiveness()) return false;
            if (minMood.eagerness() != null && mood.getEagerness() < minMood.eagerness()) return false;
            if (minMood.security() != null && mood.getSecurity() < minMood.security()) return false;
        }
        return true;
    }
}
