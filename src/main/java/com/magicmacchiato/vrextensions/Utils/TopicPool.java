package com.magicmacchiato.vrextensions.Utils;

import com.magicmacchiato.vrextensions.Dialogue.Dialogue;

import java.util.Collection;
import java.util.List;

/**
 * Every conversation topic that exists. For now these are the distinct topics of the loaded dialogues; meant to be
 * replaced by a topic registry later, so read topics only through here.
 */
public final class TopicPool {
    private TopicPool() {}

    private static volatile List<String> topics = List.of();

    /** Rebuilds the pool from the dialogues. Malformed topic ids are left out. */
    static void rebuild(Collection<Dialogue> dialogues) {
        topics = dialogues.stream()
                .flatMap(dialogue -> dialogue.conversationTopics().stream())
                .filter(Dialogue::isValidId)
                .distinct()
                .sorted()
                .toList();
    }

    /** Distinct topic ids, sorted. Empty if no dialogues are loaded. */
    public static List<String> all() {
        return topics;
    }
}
