package de.hanno.hpengine.engine.container;

import de.hanno.hpengine.engine.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface EntityContainer {
    void add(Entity entity);

    void add(List<Entity> entities);

    List<Entity> getEntities();

    @NotNull
    default Entity create() {
        Entity entity = new Entity();
        add(entity);
        return entity;
    }
}
