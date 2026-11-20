package com.magicmacchiato.vrextensions.Dialogue;

import com.google.gson.annotations.SerializedName;
import com.magicmacchiato.vrextensions.Utils.NamedVector;
import com.magicmacchiato.vrextensions.Utils.Vectors;

public record Semantic(
        @SerializedName("friendly") float friendly,
        @SerializedName("mean") float mean,
        @SerializedName("flirt") float flirt,
        @SerializedName("joke") float joke
) {
    /** Semantic strengths are expected in [0, 1]; out-of-range JSON values are clamped. */
    public Semantic {
        friendly = clamp01(friendly);
        mean = clamp01(mean);
        flirt = clamp01(flirt);
        joke = clamp01(joke);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public NamedVector.Vector getVector() {
        return Vectors.SEMANTIC_VECTOR.newVector(new float[]{friendly, mean, flirt, joke});
    }
}
