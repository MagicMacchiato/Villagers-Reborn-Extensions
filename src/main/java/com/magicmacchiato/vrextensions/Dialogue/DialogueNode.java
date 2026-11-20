package com.magicmacchiato.vrextensions.Dialogue;

import com.google.gson.annotations.SerializedName;
import com.magicmacchiato.vrextensions.Enums.Speaker;

import javax.annotation.Nullable;
import java.util.List;

/** One node of a dialogue graph, as stored in assets/vrextensions/dialogues/*.json. */
public record DialogueNode(
        @SerializedName("id") String id,
        @SerializedName("speaker") Speaker speaker,
        @SerializedName("text") String text,
        @SerializedName("semantic") @Nullable Semantic semantic,
        @SerializedName("requires") Requires requires,
        @SerializedName("next_choice") List<String> nextChoice
) {
    public DialogueNode {
        nextChoice = nextChoice == null ? List.of() : List.copyOf(nextChoice);
    }
}
