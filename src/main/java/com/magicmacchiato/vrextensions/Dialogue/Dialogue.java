package com.magicmacchiato.vrextensions.Dialogue;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One node of the new dialogue graph. Any villager or player can say any node; who may say it is decided only by
 * {@link #requirement()}. Topics ({@code <mod_id>:<topic_name>}) are what moves the listener's eagerness.
 */
public record Dialogue(
        @SerializedName("id") String id,
        @SerializedName("conversation_topics") List<String> conversationTopics,
        @SerializedName("text") String text,
        @SerializedName("semantic") Semantic semantic,
        @SerializedName("requirement") Requirement requirement,
        @SerializedName("next_choice") List<String> nextChoice,
        @SerializedName("is_opener") boolean isOpener
) {
    /** {@code <mod_id>:<name>}, the format of dialogue ids and topic ids. */
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public Dialogue {
        conversationTopics = conversationTopics == null ? List.of() : List.copyOf(conversationTopics);
        nextChoice = nextChoice == null ? List.of() : List.copyOf(nextChoice);
        semantic = semantic == null ? new Semantic(0f, 0f, 0f, 0f) : semantic;
        requirement = requirement == null ? Requirement.NONE : requirement;
    }

    public static boolean isValidId(String id) {
        return id != null && RESOURCE_ID.matcher(id).matches();
    }
}
