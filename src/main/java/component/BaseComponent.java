package component;

import engine.AppContext;
import engine.model.Entity;
import renderer.Renderer;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public abstract class BaseComponent implements Component, Serializable {
    private static final long serialVersionUID = -224913983270697337L;

    transient protected AppContext appContext;
    transient private Renderer renderer;

    private Entity entity;
    protected boolean initialized;

    public String getIdentifier() { return this.getClass() + " " + System.currentTimeMillis(); }

    public void init(AppContext appContext) {
        setAppContext(appContext);
        setRenderer(appContext.getRenderer());
        initialized = true;
    }


    public Renderer getRenderer() {
        return renderer;
    }

    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    public AppContext getAppContext() {
        return appContext;
    }

    public void setAppContext(AppContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public void setEntity(Entity entity) {
        this.entity = entity;
    }
}
