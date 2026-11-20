package com.magicmacchiato.vrextensions.Utils;

public class Vectors {
    public static final String[] SEMANTIC = {"friendly", "mean", "flirt", "joke"};
    public static final NamedVector SEMANTIC_VECTOR = new NamedVector(SEMANTIC);

    public static final String[] MOODS = {"positiveness", "eagerness", "security"};
    public static final NamedVector MOOD_VECTOR = new NamedVector(MOODS);
}
