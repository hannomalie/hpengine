package de.hanno.hpengine.engine.model;

public enum Update {
    STATIC(1),
    DYNAMIC(0);

    public final int value;
    Update(int d) {
        this.value = d;
    }

    public double getAsDouble() {
        return value;
    }

}
