package com.magicmacchiato.vrextensions.Features;

import com.magicmacchiato.vrextensions.Utils.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The conversation topics one villager is interested in, each with its own multiplier (how much hearing about it
 * raises eagerness). Rolled at random the first time the villager joins a world, saved with it, and free to change
 * afterwards.
 */
public class VillagerInterests {
    private static final String KEY = "vrextensions:interests";

    /** MOCK - how many topics a villager starts with, picked evenly in this range. */
    public static final int MIN_START_TOPICS = 1;
    public static final int MAX_START_TOPICS = 3;
    /** A rolled multiplier is this plus a random value in 0..1. */
    public static final float BASE_MULTIPLIER = 1f;

    /** Topic id to multiplier. */
    private final Map<String, Float> topics = new LinkedHashMap<>();
    /** True once rolled, so a villager that later loses every interest is not rolled again. */
    private boolean assigned;

    public boolean isAssigned() {
        return this.assigned;
    }

    /**
     * Replaces the interests with a random, distinct pick from the pool, each with a random multiplier. Does nothing
     * for an empty pool, so the villager is rolled again next time.
     */
    public void assignRandom(List<String> pool, RandomSource random) {
        if (pool.isEmpty()) return;
        int count = Random.between(random, MIN_START_TOPICS, MAX_START_TOPICS);
        this.topics.clear();
        for (String topic : Random.sample(pool, count, random)) {
            this.topics.put(topic, BASE_MULTIPLIER + Random.unit(random));
        }
        this.assigned = true;
    }

    /** Read-only view: topic id to multiplier. */
    public Map<String, Float> all() {
        return Collections.unmodifiableMap(this.topics);
    }

    public boolean has(String topic) {
        return this.topics.containsKey(topic);
    }

    /** Adds the interest, or changes its multiplier. */
    public void set(String topic, float multiplier) {
        this.topics.put(topic, multiplier);
    }

    public boolean remove(String topic) {
        return this.topics.remove(topic) != null;
    }

    /** Sum of the multipliers of the interests found among these topics (each counted once), 0 if none match. */
    public float matchSum(Collection<String> lineTopics) {
        float sum = 0f;
        for (Map.Entry<String, Float> interest : this.topics.entrySet()) {
            if (lineTopics.contains(interest.getKey())) sum += interest.getValue();
        }
        return sum;
    }

    /** The listener hears a line with these topics: its matching interests raise its eagerness. */
    public static void hear(Object listener, Collection<String> lineTopics) {
        if (listener instanceof InterestHolder interested && listener instanceof MoodHolder moody) {
            moody.getVrMood().applyInterest(interested.getVrInterests().matchSum(lineTopics));
        }
    }

    /** Writes nothing until rolled: the key's presence is what marks the villager as assigned. */
    public void save(CompoundTag tag) {
        if (!this.assigned) return;
        CompoundTag saved = new CompoundTag();
        this.topics.forEach(saved::putFloat);
        tag.put(KEY, saved);
    }

    /** Saved topics are kept even if the pool no longer has them. */
    public void load(CompoundTag tag) {
        this.topics.clear();
        this.assigned = tag.contains(KEY, Tag.TAG_COMPOUND);
        if (!this.assigned) return;
        CompoundTag saved = tag.getCompound(KEY);
        for (String topic : saved.getAllKeys()) this.topics.put(topic, saved.getFloat(topic));
    }
}
