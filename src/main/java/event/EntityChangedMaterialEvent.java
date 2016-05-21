package event;

import engine.model.Entity;

import javax.annotation.Nonnull;

public class EntityChangedMaterialEvent {
    private final Entity entity;

    public EntityChangedMaterialEvent(@Nonnull Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}
