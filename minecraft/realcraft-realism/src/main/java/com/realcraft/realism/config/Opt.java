package com.realcraft.realism.config;

import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * A single user-facing setting. Wraps a Forge config value and carries the metadata
 * (category, label, range) the in-game settings screen needs to build a widget for it.
 */
public abstract class Opt<T> {
    public final String category;
    public final String key;
    public final String label;
    public final String comment;
    public final boolean client;
    protected final ForgeConfigSpec.ConfigValue<T> value;

    protected Opt(String category, String key, String label, String comment, boolean client, ForgeConfigSpec.ConfigValue<T> value) {
        this.category = category;
        this.key = key;
        this.label = label;
        this.comment = comment;
        this.client = client;
        this.value = value;
    }

    public T get() {
        return value.get();
    }

    public void set(T newValue) {
        value.set(newValue);
    }

    public T getDefault() {
        return value.getDefault();
    }

    public Component title() {
        return Component.translatableWithFallback("realcraft.option." + key, label);
    }

    public Component description() {
        return Component.translatableWithFallback("realcraft.option." + key + ".desc", comment);
    }

    public static final class Bool extends Opt<Boolean> {
        Bool(String category, String key, String label, String comment, boolean client, ForgeConfigSpec.BooleanValue value) {
            super(category, key, label, comment, client, value);
        }

        public boolean on() {
            return value.get();
        }
    }

    public static final class Int extends Opt<Integer> {
        public final int min;
        public final int max;

        Int(String category, String key, String label, String comment, boolean client, ForgeConfigSpec.IntValue value, int min, int max) {
            super(category, key, label, comment, client, value);
            this.min = min;
            this.max = max;
        }

        public int val() {
            return value.get();
        }
    }

    public static final class Dbl extends Opt<Double> {
        public final double min;
        public final double max;

        Dbl(String category, String key, String label, String comment, boolean client, ForgeConfigSpec.DoubleValue value, double min, double max) {
            super(category, key, label, comment, client, value);
            this.min = min;
            this.max = max;
        }

        public double val() {
            return value.get();
        }

        public float valF() {
            return value.get().floatValue();
        }
    }
}
