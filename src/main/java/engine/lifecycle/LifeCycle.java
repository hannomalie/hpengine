package engine.lifecycle;


import engine.AppContext;
import engine.model.EntityFactory;
import renderer.material.MaterialFactory;

public interface LifeCycle {

    default void init(AppContext appContext) { setAppContext(appContext); }

    boolean isInitialized();

    default void update(float seconds) { }
    @SuppressWarnings("unused")
    default void destroy(AppContext appContext) { }

    void setAppContext(AppContext appContext);
    AppContext getAppContext();

    default MaterialFactory getMaterialFactory() {
        return getAppContext().getRenderer().getMaterialFactory();
    }
    default EntityFactory getEntityFactory() {
        return getAppContext().getEntityFactory();
    }
}
