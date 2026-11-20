package com.magicmacchiato.vrextensions.Utils;


import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NamedVector {
    private final Map<String, Integer> NAME_MAP;
    private final int DIMENSION;

    public NamedVector(String[] names) {
        if (names == null) {
            throw new IllegalArgumentException("Names array cannot be null.");
        }
        NAME_MAP = new ConcurrentHashMap<>();
        this.DIMENSION = names.length;
        for (int i = 0; i < this.DIMENSION; i++) {
            NAME_MAP.put(names[i], i);
        }
    }

    public Vector newVector() {
        return new Vector();
    }

    public Vector newVector(float[] values) {
        if (values == null) {
            throw new IllegalArgumentException("Values array cannot be null.");
        }
        if (values.length > this.DIMENSION) {
            throw new IllegalArgumentException("Values length exceeds the vector dimension.");
        }
        return new Vector(values);
    }

    public class Vector {
        public final float[] VECTOR;

        public Vector() {
            this.VECTOR = new float[DIMENSION];
        }

        public Vector(float[] values) {
            this.VECTOR = new float[DIMENSION];
            System.arraycopy(values, 0, this.VECTOR, 0, Math.min(values.length, DIMENSION));
        }

        public float at(int index) {
            return this.VECTOR[index];
        }

        public float at(String name) {
            Integer index = NAME_MAP.get(name);
            if (index == null) {
                throw new IllegalArgumentException("Name not found in vector schema: " + name);
            }
            return this.VECTOR[index];
        }

        public float dot(Vector other) {
            if (this.VECTOR.length != other.VECTOR.length) {
                throw new IllegalArgumentException("Vector dimensions do not match.");
            }
            float sum = 0;
            for (int i = 0; i < this.VECTOR.length; i++) {
                sum += (this.VECTOR[i] * other.VECTOR[i]);
            }
            return sum;
        }
    }
}

