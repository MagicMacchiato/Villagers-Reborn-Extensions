package com.magicmacchiato.vrextensions.Dialogue;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

/** Lowest mood a line needs. An axis left out of the JSON is not gated. */
public record MinMood(
        @SerializedName("positiveness") @Nullable Float positiveness,
        @SerializedName("eagerness") @Nullable Float eagerness,
        @SerializedName("security") @Nullable Float security
) {}
