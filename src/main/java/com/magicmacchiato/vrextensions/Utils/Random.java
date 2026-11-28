package com.magicmacchiato.vrextensions.Utils;

import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Every random draw of this mod goes through here, so that features can later switch to weighted randomness in one
 * place. Draws are uniform except {@link #gaussian} and {@link #pickWeighted}. Methods taking a {@link RandomSource} use it
 * (normally the entity's own); the others use the thread's generator.
 */
public final class Random {
    private Random() {}

    /** True with the given probability (0..1). */
    public static boolean chance(RandomSource random, float probability) {
        return random.nextFloat() < probability;
    }

    /** A value from 0 (included) to 1 (excluded). */
    public static float unit(RandomSource random) {
        return random.nextFloat();
    }

    /** A whole number from min to max, both included. */
    public static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** A normally distributed value with mean 0 and the given standard deviation. */
    public static double gaussian(RandomSource random, double deviation) {
        return random.nextGaussian() * deviation;
    }

    /**
     * Turns scores into probabilities that sum to 1, in the same order: e^(score/t) / sum of e^(score/t). Higher scores
     * get higher probabilities. The temperature t must be above 0: below 1 favors the highest score more, above 1
     * evens the probabilities out. The scores must be finite; an empty array gives an empty array.
     */
    public static double[] softmax(double[] scores, double temperature) {
        double max = Double.NEGATIVE_INFINITY;
        for (double score : scores) max = Math.max(max, score);

        // Subtracting the max leaves the result unchanged and keeps exp from overflowing.
        double[] probabilities = new double[scores.length];
        double sum = 0;
        for (int i = 0; i < scores.length; i++) {
            probabilities[i] = Math.exp((scores[i] - max) / temperature);
            sum += probabilities[i];
        }
        for (int i = 0; i < probabilities.length; i++) probabilities[i] /= sum;
        return probabilities;
    }

    /**
     * One element, element i with probability {@code probabilities[i]} (same length as the pool, summing to 1, e.g.
     * from {@link #softmax}). Null if the pool is empty.
     */
    @Nullable
    public static <T> T pickWeighted(List<T> pool, double[] probabilities, RandomSource random) {
        double roll = random.nextDouble();
        for (int i = 0; i < pool.size(); i++) {
            roll -= probabilities[i];
            if (roll < 0) return pool.get(i);
        }
        // Rounding can leave a tiny remainder.
        return pool.isEmpty() ? null : pool.get(pool.size() - 1);
    }

    /** One element, or null if the list is empty. */
    @Nullable
    public static <T> T pick(List<T> pool, RandomSource random) {
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    /** One element, or null if the list is empty. */
    @Nullable
    public static <T> T pick(List<T> pool) {
        return pool.isEmpty() ? null : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** Up to {@code count} elements from different positions, in random order. The pool is not changed. */
    public static <T> List<T> sample(List<T> pool, int count, RandomSource random) {
        List<T> remaining = new ArrayList<>(pool);
        List<T> picked = new ArrayList<>();
        while (picked.size() < count && !remaining.isEmpty()) {
            picked.add(remaining.remove(random.nextInt(remaining.size())));
        }
        return picked;
    }

    /** Up to {@code count} elements from different positions, in random order. The pool is not changed. */
    public static <T> List<T> sample(List<T> pool, int count) {
        List<T> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return List.copyOf(shuffled.subList(0, Math.min(count, shuffled.size())));
    }
}
