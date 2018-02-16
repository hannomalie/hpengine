package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.scene.Scene;

import java.io.Serializable;

public interface Component extends LifeCycle, Serializable {

	Entity getEntity();

	default void update(Engine engine, float seconds) {}

	String getIdentifier();

	default void initAfterAdd(Entity entity) {}

    default void registerInScene(Scene scene, Engine engine) {}
}
