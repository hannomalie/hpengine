package de.hanno.hpengine.engine.model;

public enum Update {
    STATIC(1),
    DYNAMIC(0);

    private final double d;
    Update(double d) {
        this.d = d;
    }

    public double getAsDouble() {
        return d;
    }

}
