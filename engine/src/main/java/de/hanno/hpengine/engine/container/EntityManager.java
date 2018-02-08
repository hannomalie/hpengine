package de.hanno.hpengine.engine.container;

import de.hanno.hpengine.engine.model.Entity;

import java.util.List;

public interface EntityManager {
    void add(Entity entity);

    void add(List<Entity> entities);

    List<Entity> getEntities();
}
