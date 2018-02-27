package de.hanno.hpengine.engine.graphics;

public interface GpuCommandSync {
    default boolean isSignaled() {
        return true;
    }

    default void delete() { }
}
