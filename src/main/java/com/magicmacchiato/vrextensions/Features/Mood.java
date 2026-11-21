package com.magicmacchiato.vrextensions.Features;

import com.javic.slimpatch.entity.VillagerPersonality;
import com.magicmacchiato.vrextensions.Dialogue.Semantic;
import com.magicmacchiato.vrextensions.Utils.NamedVector;
import com.magicmacchiato.vrextensions.Utils.Vectors;
import com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod;

import static com.magicmacchiato.vrextensions.VillagersRebornExtensionsMod.LOGGER;
import net.minecraft.nbt.CompoundTag;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Mood {
    private static final String POSITIVENESS_KEY = "vrextensions:mood.positiveness";
    private static final String EAGERNESS_KEY = "vrextensions:mood.eagerness";
    private static final String SECURITY_KEY = "vrextensions:mood.security";

    /**
     * How each personality reacts to each semantic, as a vector in {@link Vectors#SEMANTIC} order.
     * Each value is in [-1, 1]: negative = dislikes, positive = likes. Loaded from
     * assets/vrextensions/personality_reactions.json by {@link #loadReactions(Path)} (MOCK values - to be tuned).
     */
    private static volatile Map<VillagerPersonality, NamedVector.Vector> personalityReactions = Map.of();

    public static Map<VillagerPersonality, NamedVector.Vector> getPersonalityReactions() {
        return personalityReactions;
    }

    /**
     * Loads personality reactions from JSON of the form
     * {@code {"FRIENDLY": {"friendly": 0.8, "mean": -0.6, "flirt": 0.2, "joke": 0.5}, ...}}.
     * Entries that are unknown, incomplete, non-finite or outside [-1, 1] are skipped with an error, and every
     * personality missing afterwards is reported (its reaction then counts as 0).
     */
    public static void loadReactions(Path file) {
        Map<VillagerPersonality, NamedVector.Vector> map = new EnumMap<>(VillagerPersonality.class);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                VillagerPersonality personality = parsePersonality(entry.getKey());
                if (personality == null) {
                    LOGGER.error("personality_reactions: unknown personality '{}'.", entry.getKey());
                    continue;
                }
                NamedVector.Vector vector = parseReaction(personality, entry.getValue());
                if (vector != null) map.put(personality, vector);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read personality reactions {}", file, e);
        }
        for (VillagerPersonality p : VillagerPersonality.values()) {
            if (!map.containsKey(p)) LOGGER.error("personality_reactions: no valid entry for {}; its reactions count as 0.", p);
        }
        personalityReactions = Collections.unmodifiableMap(map);
        LOGGER.info("Loaded personality reactions for {} personalities.", map.size());
    }

    private static VillagerPersonality parsePersonality(String name) {
        for (VillagerPersonality p : VillagerPersonality.values()) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static NamedVector.Vector parseReaction(VillagerPersonality personality, JsonElement element) {
        if (!element.isJsonObject()) {
            LOGGER.error("personality_reactions: {} must be an object.", personality);
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        for (String key : obj.keySet()) {
            if (!List.of(Vectors.SEMANTIC).contains(key)) {
                LOGGER.error("personality_reactions: {} has unknown semantic '{}'.", personality, key);
                return null;
            }
        }
        float[] values = new float[Vectors.SEMANTIC.length];
        for (int i = 0; i < values.length; i++) {
            String name = Vectors.SEMANTIC[i];
            JsonElement v = obj.get(name);
            if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
                LOGGER.error("personality_reactions: {} is missing a numeric '{}'.", personality, name);
                return null;
            }
            float f = v.getAsFloat();
            if (!Float.isFinite(f) || f < -1f || f > 1f) {
                LOGGER.error("personality_reactions: {}.{} = {} is outside [-1, 1].", personality, name, f);
                return null;
            }
            values[i] = f;
        }
        return Vectors.SEMANTIC_VECTOR.newVector(values);
    }

    /** How strongly a reaction score (about -2..2) shifts the mood. */
    public static final float REACTION_TO_MOOD = 2.0f;
    public static final float MOOD_LIMIT = 5.0f;

    /** MOCK - damage multiplier bonus in 0..1: security drop = damage * (1 + DAMAGE_COEFFICIENT) + ... */
    public static final float DAMAGE_COEFFICIENT = 0.5f;
    /** Avoids division by zero when current health reaches 0. */
    public static final float HEALTH_EPSILON = 0.1f;
    /** MOCK - scales the raw drop down to the +-MOOD_LIMIT range. */
    public static final float SECURITY_SCALE = 0.1f;

    private float positiveness;
    private float eagerness;
    private float security;

    public Mood(float positiveness, float eagerness, float security) {
        this.positiveness = positiveness;
        this.eagerness = eagerness;
        this.security = security;
    }

    public Mood() {
        this(0f, 0f, 0f);
    }

    public float getPositiveness() {
        return this.positiveness;
    }

    public float getEagerness() {
        return this.eagerness;
    }

    public float getSecurity() {return this.security;}

    public void setPositiveness(float value) {this.positiveness = value;}

    public void setEagerness(float value) {this.eagerness = value;}

    public void setSecurity(float security) {
        this.security = security;
    }

    /** Reaction of a personality to a dialogue's semantics: dot(personality reaction, semantic). */
    public static float reaction(VillagerPersonality personality, Semantic semantic) {
        NamedVector.Vector reaction = personalityReactions.get(personality);
        if (reaction == null || semantic == null) return 0f;
        return reaction.dot(semantic.getVector());
    }

    /** Applies a reaction score to the mood (positiveness for now), clamped to +-MOOD_LIMIT. */
    public void applyReaction(float reaction) {
        float value = this.positiveness + reaction * REACTION_TO_MOOD;
        this.positiveness = Math.max(-MOOD_LIMIT, Math.min(MOOD_LIMIT, value));
    }

    /**
     * Security drop from damage that actually got through (after armor etc.):
     * {@code damage * (1 + DAMAGE_COEFFICIENT) + (1 + maxHealth / (currentHealth + epsilon))}, times
     * {@link #SECURITY_SCALE}. {@code currentHealth} is the health left after the hit.
     * MOCK formula - to be redesigned.
     */
    public static float securityDrop(float damage, float maxHealth, float currentHealth) {
        if (!(damage > 0f)) return 0f;
        float raw = damage * (1f + DAMAGE_COEFFICIENT)
                + (1f + maxHealth / (Math.max(currentHealth, 0f) + HEALTH_EPSILON));
        return raw * SECURITY_SCALE;
    }

    /**
     * Security gain from healing: {@code healed * (1 + currentHealth / maxHealth)}, times
     * {@link #SECURITY_SCALE}. No flat term and no damage coefficient, so recovery is slower than the
     * drop from the same amount of damage. {@code currentHealth} is the health after the heal.
     * MOCK formula - to be redesigned.
     */
    public static float securityGain(float healed, float maxHealth, float currentHealth) {
        if (!(healed > 0f) || !(maxHealth > 0f)) return 0f;
        return healed * (1f + Math.min(currentHealth / maxHealth, 1f)) * SECURITY_SCALE;
    }

    /** Raises security by the gain for this heal, clamped to +-MOOD_LIMIT. */
    public void applyHeal(float healed, float maxHealth, float currentHealth) {
        float value = this.security + securityGain(healed, maxHealth, currentHealth);
        this.security = Math.max(-MOOD_LIMIT, Math.min(MOOD_LIMIT, value));
    }

    /** Lowers security by the drop for this hit, clamped to +-MOOD_LIMIT. */
    public void applyDamage(float damage, float maxHealth, float currentHealth) {
        float value = this.security - securityDrop(damage, maxHealth, currentHealth);
        this.security = Math.max(-MOOD_LIMIT, Math.min(MOOD_LIMIT, value));
    }

    /** Console line after a mood update: {@code <npc_uuid>: positiveness: <p>, security: <s>}. */
    public void logState(UUID npcUuid) {
        VillagersRebornExtensionsMod.LOGGER.info("{}: positiveness: {}, security: {}",
                npcUuid, this.positiveness, this.security);
    }

    public void save(CompoundTag tag) {
        tag.putFloat(POSITIVENESS_KEY, this.positiveness);
        tag.putFloat(EAGERNESS_KEY, this.eagerness);
        tag.putFloat(SECURITY_KEY, this.security);
    }

    public void load(CompoundTag tag) {
        this.positiveness = tag.contains(POSITIVENESS_KEY) ? tag.getFloat(POSITIVENESS_KEY) : 0f;
        this.eagerness = tag.contains(EAGERNESS_KEY) ? tag.getFloat(EAGERNESS_KEY) : 0f;
        this.security = tag.contains(SECURITY_KEY) ? tag.getFloat(SECURITY_KEY) : 0f;
    }
}
