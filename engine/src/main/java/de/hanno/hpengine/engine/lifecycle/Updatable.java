package de.hanno.hpengine.engine.lifecycle;


public interface Updatable {
    default void update(float seconds) { }
}
