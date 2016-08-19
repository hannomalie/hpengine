package component;

import engine.lifecycle.LifeCycle;
import engine.model.Entity;

import java.io.Serializable;

public interface Component extends LifeCycle, Serializable {

	void setEntity(Entity entity);
	Entity getEntity();

	default void update(float seconds) {}

	String getIdentifier();

	default void initAfterAdd(Entity entity) { if(entity.isInitialized()) { init(); }}
}
