package com.magicmacchiato.vrextensions.Features;

import com.javic.slimpatch.entity.VillagerPersonality;
import com.magicmacchiato.vrextensions.Dialogue.Semantic;
import com.magicmacchiato.vrextensions.Utils.NamedVector;
import com.magicmacchiato.vrextensions.Utils.Vectors;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class Mood {
    private static final String POSITIVENESS_KEY = "vrextensions:mood.positiveness";
    private static final String EAGERNESS_KEY = "vrextensions:mood.eagerness";
    private static final String SECURITY_KEY = "vrextensions:mood.security";

    /**
     * How each personality reacts to each semantic, in {@link Vectors#SEMANTIC} order
     * (friendly, mean, flirt, joke). Each value is in [-1, 1]: negative = dislikes, positive = likes.
     * MOCK VALUES - to be tuned.
     */
    public static final Map<VillagerPersonality, NamedVector.Vector> PERSONALITY_REACTIONS;

    static {
        Map<VillagerPersonality, NamedVector.Vector> map = new EnumMap<>(VillagerPersonality.class);
        //                                                                          friendly mean   flirt   joke
        map.put(VillagerPersonality.FRIENDLY, Vectors.SEMANTIC_VECTOR.newVector(new float[]{ 0.8f, -0.6f,  0.2f,  0.5f}));
        map.put(VillagerPersonality.MEAN,     Vectors.SEMANTIC_VECTOR.newVector(new float[]{-0.5f,  0.6f, -0.4f, -0.2f}));
        map.put(VillagerPersonality.SHY,      Vectors.SEMANTIC_VECTOR.newVector(new float[]{ 0.5f, -0.5f, -0.6f,  0.2f}));
        map.put(VillagerPersonality.BRAVE,    Vectors.SEMANTIC_VECTOR.newVector(new float[]{ 0.4f,  0.2f,  0.4f,  0.3f}));
        map.put(VillagerPersonality.GRUMPY,   Vectors.SEMANTIC_VECTOR.newVector(new float[]{-0.5f,  0.5f, -0.5f, -0.6f}));
        map.put(VillagerPersonality.GREEDY,   Vectors.SEMANTIC_VECTOR.newVector(new float[]{ 0.0f, -0.3f,  0.3f,  0.0f}));
        map.put(VillagerPersonality.ROMANTIC, Vectors.SEMANTIC_VECTOR.newVector(new float[]{ 0.4f, -0.7f,  0.9f,  0.4f}));
        map.put(VillagerPersonality.WISE,     Vectors.SEMANTIC_VECTOR.newVector(new float[]{ 0.5f, -0.6f, -0.2f,  0.3f}));
        PERSONALITY_REACTIONS = Collections.unmodifiableMap(map);
    }

    /** How strongly a reaction score (about -2..2) shifts the mood. */
    public static final float REACTION_TO_MOOD = 2.0f;
    public static final float MOOD_LIMIT = 5.0f;

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
        NamedVector.Vector reaction = PERSONALITY_REACTIONS.get(personality);
        if (reaction == null || semantic == null) return 0f;
        return reaction.dot(semantic.getVector());
    }

    /** Applies a reaction score to the mood (positiveness for now), clamped to +-MOOD_LIMIT. */
    public void applyReaction(float reaction) {
        float value = this.positiveness + reaction * REACTION_TO_MOOD;
        this.positiveness = Math.max(-MOOD_LIMIT, Math.min(MOOD_LIMIT, value));
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
