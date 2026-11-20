package com.magicmacchiato.vrextensions.Dialogue;

import com.google.gson.annotations.SerializedName;
import com.magicmacchiato.vrextensions.Utils.NamedVector;
import com.magicmacchiato.vrextensions.Utils.Vectors;

public record MinMood(
        @SerializedName("positiveness") float positiveness,
        @SerializedName("eagerness") float eagerness,
        @SerializedName("security") float security
) {
    public NamedVector.Vector getVector() {
        return Vectors.MOOD_VECTOR.newVector(new float[]{positiveness, eagerness, security});
    }
}
