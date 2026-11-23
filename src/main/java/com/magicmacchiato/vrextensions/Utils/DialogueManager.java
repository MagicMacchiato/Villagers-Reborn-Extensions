package com.magicmacchiato.vrextensions.Utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.magicmacchiato.vrextensions.Dialogue.DialogueNode;
import com.magicmacchiato.vrextensions.Enums.Speaker;
import com.magicmacchiato.vrextensions.Features.Mood;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;
import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.MOD_ID;

public class DialogueManager {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, DialogueNode>>() {}.getType();

    private static final Map<String, DialogueNode> DIALOGUE_MAP = new ConcurrentHashMap<>();

    /** Loads every *.json under assets/vrextensions/dialogues/, replacing previously loaded nodes. */
    public static void loadAll() {
        DIALOGUE_MAP.clear();

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
        LOGGER.info("Loaded {} dialogue nodes from {} file(s).", DIALOGUE_MAP.size(), files.size());
    }

    private static void loadFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, DialogueNode> nodes = GSON.fromJson(reader, MAP_TYPE);
            if (nodes == null) return;
            for (var entry : nodes.entrySet()) {
                if (entry.getValue() == null) continue;
                if (DIALOGUE_MAP.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                    LOGGER.warn("Duplicate dialogue id '{}' in {}; keeping the first.", entry.getKey(), file);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read dialogue file {}", file, e);
        }
    }

    private static void validate() {
        DIALOGUE_MAP.forEach((key, node) -> {
            if (!key.equals(node.id())) {
                LOGGER.warn("Dialogue key '{}' does not match its id '{}'.", key, node.id());
            }
            for (String next : node.nextChoice()) {
                if (!DIALOGUE_MAP.containsKey(next)) {
                    LOGGER.warn("Dialogue '{}' references unknown next_choice '{}'.", key, next);
                }
            }
        });
    }

    /**
     * Picks a random villager node to open a conversation: a node nobody points to via next_choice (a topic root)
     * that has at least one player choice. Falls back to any villager node with choices.
     */
    public static DialogueNode randomOpener(Predicate<DialogueNode> allowed) {
        java.util.Set<String> referenced = new java.util.HashSet<>();
        for (DialogueNode node : DIALOGUE_MAP.values()) referenced.addAll(node.nextChoice());

        List<DialogueNode> roots = new ArrayList<>();
        List<DialogueNode> any = new ArrayList<>();
        for (DialogueNode node : DIALOGUE_MAP.values()) {
            if (node.speaker() != Speaker.villager || !allowed.test(node) || choicesOf(node, allowed).isEmpty()) continue;
            any.add(node);
            if (!referenced.contains(node.id())) roots.add(node);
        }
        List<DialogueNode> pool = roots.isEmpty() ? any : roots;
        return pool.isEmpty() ? null : pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** The player nodes the player can choose from after the given villager node. */
    public static List<DialogueNode> choicesOf(DialogueNode villagerNode, Predicate<DialogueNode> allowed) {
        List<DialogueNode> choices = new ArrayList<>();
        for (String id : villagerNode.nextChoice()) {
            DialogueNode next = DIALOGUE_MAP.get(id);
            if (next != null && next.speaker() == Speaker.player && allowed.test(next)) choices.add(next);
        }
        return choices;
    }

    /** Picks a random villager reply among the chosen node's next_choice, or null if there is none. */
    public static DialogueNode randomReply(DialogueNode chosen, Predicate<DialogueNode> allowed) {
        List<DialogueNode> replies = new ArrayList<>();
        for (String id : chosen.nextChoice()) {
            DialogueNode next = DIALOGUE_MAP.get(id);
            if (next != null && next.speaker() == Speaker.villager && allowed.test(next)) replies.add(next);
        }
        return replies.isEmpty() ? null : replies.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(replies.size()));
    }

    /**
     * Picks a random node among the chosen node's next_choice regardless of who speaks it (villager-to-villager
     * conversations), or null if none is allowed.
     */
    public static DialogueNode randomNext(DialogueNode current, Predicate<DialogueNode> allowed) {
        List<DialogueNode> options = new ArrayList<>();
        for (String id : current.nextChoice()) {
            DialogueNode next = DIALOGUE_MAP.get(id);
            if (next != null && allowed.test(next)) options.add(next);
        }
        return options.isEmpty() ? null : options.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(options.size()));
    }

    public static DialogueNode get(String id) {
        return DIALOGUE_MAP.get(id);
    }

    public static Map<String, DialogueNode> getAll() {
        return Collections.unmodifiableMap(DIALOGUE_MAP);
    }
}
