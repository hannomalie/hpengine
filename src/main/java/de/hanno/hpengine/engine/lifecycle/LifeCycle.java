package de.hanno.hpengine.engine.lifecycle;


public interface LifeCycle {

    default void init() { }

    boolean isInitialized();

    default void update(float seconds) { }
    default void destroy() { }
}
