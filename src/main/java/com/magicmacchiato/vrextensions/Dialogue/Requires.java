package com.magicmacchiato.vrextensions.Dialogue;

import com.google.gson.annotations.SerializedName;
import com.javic.slimpatch.entity.VillagerRelationshipStage;
import com.magicmacchiato.vrextensions.Features.Mood;

import javax.annotation.Nullable;
import java.util.List;

public record Requires(
        @SerializedName("min_relationship") float minRelationship,
        @SerializedName("stage") List<VillagerRelationshipStage> stages,
        @SerializedName("min_mood") @Nullable MinMood minMood
) {
    public Requires {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /** True if the given relationship hearts, stage and mood satisfy every requirement. */
    public boolean isMetBy(float relationship, VillagerRelationshipStage stage, Mood mood) {
        if (relationship < minRelationship) return false;
        if (!stages.isEmpty() && !stages.contains(stage)) return false;
        if (minMood != null) {
            if (mood.getPositiveness() < minMood.positiveness()) return false;
            if (mood.getEagerness() < minMood.eagerness()) return false;
            if (mood.getSecurity() < minMood.security()) return false;
        }
        return true;
    }
}
