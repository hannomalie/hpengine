package de.hanno.hpengine.engine.lifecycle;


import de.hanno.hpengine.engine.Engine;

public interface LifeCycle {
    default void init(Engine engine) { }
    default void update(Engine engine, float seconds) { }
    default void destroy() { }
}
