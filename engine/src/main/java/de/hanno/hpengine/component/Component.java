package de.hanno.hpengine.component;

import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.scene.Scene;

import java.io.Serializable;

public interface Component extends LifeCycle, Serializable {

	void setEntity(Entity entity);
	Entity getEntity();

	default void update(float seconds) {}

	String getIdentifier();

	default void initAfterAdd(Entity entity) { if(entity.isInitialized()) { init(); }}

    default void registerInScene(Scene scene) {}
}
