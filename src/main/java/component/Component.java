package component;

import engine.lifecycle.LifeCycle;

import java.io.Serializable;

public interface Component extends LifeCycle, Serializable {

	default void update(float seconds) {}

	Class<? extends Component> getIdentifier();

}
