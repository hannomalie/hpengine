package engine.lifecycle;


public interface LifeCycle {

    default void init() { }

    boolean isInitialized();

    default void update(float seconds) { }
    default void destroy() { }
}
