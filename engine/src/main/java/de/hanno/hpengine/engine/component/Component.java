package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.lifecycle.Updatable;

import java.io.Serializable;

public interface Component extends Updatable, Serializable {

	Entity getEntity();

	default void update(float seconds) {}

	String getIdentifier();

	default void destroy() { }
}
