package com.magicmacchiato.vrextensions.Utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.magicmacchiato.vrextensions.Dialogue.Dialogue;
import com.magicmacchiato.vrextensions.Features.Mood;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;
import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

public class DialogueManager {

    private static final Gson GSON = new Gson();

    /** Most choices offered to the player at once; the dialogue screen cannot fit a node's whole next_choice. */
    public static final int MAX_CHOICES = 4;

    private static final Map<String, Dialogue> DIALOGUE_MAP = new ConcurrentHashMap<>();
    private static volatile List<Dialogue> openers = List.of();

    /** Loads every *.json under assets/vrextensions/dialogues/, replacing previously loaded nodes. */
    public static void loadAll() {
        DIALOGUE_MAP.clear();
        openers = List.of();
        TopicPool.rebuild(List.of());

        var modFile = ModList.get().getModFileById(MOD_ID);
        if (modFile == null) {
            LOGGER.error("Could not find mod file for '{}'; no dialogues loaded.", MOD_ID);
            return;
        }

        // Not inside dialogues/, which is scanned for dialogue nodes.
        Path reactions = modFile.getFile().findResource("assets", MOD_ID, "personality_reactions.json");
        if (Files.isRegularFile(reactions)) {
            Mood.loadReactions(reactions);
        } else {
            LOGGER.error("Personality reactions file not found: {}", reactions);
        }

        Path dir = modFile.getFile().findResource("assets", MOD_ID, "dialogues");
        if (!Files.isDirectory(dir)) {
            LOGGER.warn("Dialogue directory not found: {}", dir);
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            LOGGER.error("Failed to list dialogue directory {}", dir, e);
            return;
        }

        for (Path file : files) loadFile(file);

        validate();
        openers = DIALOGUE_MAP.values().stream().filter(Dialogue::isOpener).toList();
        TopicPool.rebuild(DIALOGUE_MAP.values());
        LOGGER.info("Loaded {} dialogue nodes ({} openers, {} topics) from {} file(s).",
                DIALOGUE_MAP.size(), openers.size(), TopicPool.all().size(), files.size());
    }

    /** A file is a JSON array of {@link Dialogue} nodes. A broken node is skipped, the rest still load. */
    private static void loadFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                try {
                    Dialogue node = GSON.fromJson(element, Dialogue.class);
                    if (!Dialogue.isValidId(node.id()) || node.text() == null) {
                        LOGGER.warn("Skipping dialogue with a bad id or no text in {}: '{}'.", file, node.id());
                    } else if (DIALOGUE_MAP.putIfAbsent(node.id(), node) != null) {
                        LOGGER.warn("Duplicate dialogue id '{}' in {}; keeping the first.", node.id(), file);
                    }
                } catch (RuntimeException e) {
                    // e.g. an unknown stage name, which Gson turns into a null list entry
                    LOGGER.warn("Skipping unreadable dialogue in {}: {}", file, element, e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read dialogue file {}", file, e);
        }
    }

    /** Warns about unknown next_choice ids, referenced openers, unreachable nodes and malformed topic ids. */
    private static void validate() {
        Set<String> reached = new HashSet<>();
        List<String> pending = new ArrayList<>();
        DIALOGUE_MAP.values().forEach(node -> {
            if (node.isOpener() && reached.add(node.id())) pending.add(node.id());
            for (String next : node.nextChoice()) {
                Dialogue target = DIALOGUE_MAP.get(next);
                if (target == null) {
                    LOGGER.warn("Dialogue '{}' references unknown next_choice '{}'.", node.id(), next);
                } else if (target.isOpener()) {
                    LOGGER.warn("Dialogue '{}' refers back to opener '{}'.", node.id(), next);
                }
            }
            for (String topic : node.conversationTopics()) {
                if (!Dialogue.isValidId(topic)) LOGGER.warn("Dialogue '{}' has a malformed topic '{}'.", node.id(), topic);
            }
        });
        while (!pending.isEmpty()) {
            for (String next : DIALOGUE_MAP.get(pending.remove(pending.size() - 1)).nextChoice()) {
                if (DIALOGUE_MAP.containsKey(next) && reached.add(next)) pending.add(next);
            }
        }
        if (reached.size() < DIALOGUE_MAP.size()) {
            LOGGER.warn("{} dialogue node(s) cannot be reached from any opener.", DIALOGUE_MAP.size() - reached.size());
        }
    }

    /**
     * A random opener the speaker may say that has at least one allowed follow-up, or null.
     *
     * @param opening  requirements of the one who opens
     * @param followUp requirements of the one who answers
     */
    public static Dialogue randomOpener(Predicate<Dialogue> opening, Predicate<Dialogue> followUp) {
        List<Dialogue> pool = openers.stream()
                .filter(node -> opening.test(node) && !allowedNext(node, followUp).isEmpty())
                .toList();
        return Random.pick(pool);
    }

    /** Up to {@link #MAX_CHOICES} random lines the player can choose from after the given line. */
    public static List<Dialogue> choicesOf(Dialogue line, Predicate<Dialogue> allowed) {
        return Random.sample(allowedNext(line, allowed), MAX_CHOICES);
    }

    /** A random allowed line among the current line's next_choice, or null if there is none. */
    public static Dialogue randomNext(Dialogue current, Predicate<Dialogue> allowed) {
        return Random.pick(allowedNext(current, allowed));
    }

    private static List<Dialogue> allowedNext(Dialogue line, Predicate<Dialogue> allowed) {
        List<Dialogue> options = new ArrayList<>();
        for (String id : line.nextChoice()) {
            Dialogue next = DIALOGUE_MAP.get(id);
            if (next != null && allowed.test(next)) options.add(next);
        }
        return options;
    }

    public static Dialogue get(String id) {
        return DIALOGUE_MAP.get(id);
    }

    public static Map<String, Dialogue> getAll() {
        return Collections.unmodifiableMap(DIALOGUE_MAP);
    }
}
