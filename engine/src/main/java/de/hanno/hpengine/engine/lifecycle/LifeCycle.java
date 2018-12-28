package de.hanno.hpengine.engine.lifecycle;


import de.hanno.hpengine.engine.backend.EngineContext;

public interface LifeCycle {
    default void init(EngineContext engine) { }
    default void update(float seconds) { }
    default void destroy() { }
}
