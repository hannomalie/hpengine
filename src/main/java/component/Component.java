package component;

import engine.lifecycle.LifeCycle;
import engine.model.Entity;

import java.io.Serializable;

public interface Component extends LifeCycle, Serializable {

	void setEntity(Entity entity);
	Entity getEntity();

	default void update(float seconds) {}

	Class<? extends Component> getIdentifier();

	default void initAfterAdd(Entity entity) {}
}
