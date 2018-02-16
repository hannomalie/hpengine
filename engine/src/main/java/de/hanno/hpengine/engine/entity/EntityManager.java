package de.hanno.hpengine.engine.entity;

import de.hanno.hpengine.engine.container.EntityContainer;
import de.hanno.hpengine.engine.container.SimpleContainer;
import de.hanno.hpengine.engine.event.bus.EventBus;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.List;

public class EntityManager {

    EntityContainer entities = new SimpleContainer();

    public EntityManager(EventBus eventbus) {
        eventbus.register(this);
    }

    @NotNull
    public Entity create() {
        Entity entity = new Entity();
        return entity;
    }

    @NotNull
    public Entity create(String name) {
        return create(new Vector3f(), name);
    }

    @NotNull
    public Entity create(Vector3f position, String name) {
        Entity entity = new Entity(name, position);

        return entity;
    }

    @NotNull
    public List<Entity> getEntities() {
        return entities.getEntities();
    }

    public void add(Entity entity) {
        entities.add(entity);
    }

    public void add(@NotNull List<Entity> entities) {
        this.entities.add(entities);
    }
}
