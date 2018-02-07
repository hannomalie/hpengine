package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;

import java.io.Serializable;

public abstract class BaseComponent implements Component, Serializable {
    private static final long serialVersionUID = -224913983270697337L;

    protected Entity entity;
    transient protected boolean initialized;

    public String getIdentifier() { return this.getClass() + " " + System.currentTimeMillis(); }

    @Override
    public void init(Engine engine) {
        initialized = true;
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
