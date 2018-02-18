package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.entity.Entity;

import java.io.Serializable;

public abstract class BaseComponent implements Component, Serializable {
    private static final long serialVersionUID = -224913983270697337L;

    protected Entity entity;

    public String getIdentifier() { return this.getClass() + " " + System.currentTimeMillis(); }

    @Override
    public Entity getEntity() {
        return entity;
    }

}
