package de.hanno.hpengine.engine.event;

import de.hanno.hpengine.engine.entity.Entity;

public class UpdateChangedEvent {
    private final Entity entity;

    public UpdateChangedEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}
