package event;

import engine.model.Entity;

public class UpdateChangedEvent {
    private final Entity entity;

    public UpdateChangedEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}
