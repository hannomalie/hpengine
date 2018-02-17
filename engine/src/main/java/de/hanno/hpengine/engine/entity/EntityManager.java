package de.hanno.hpengine.engine.entity;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.container.EntityContainer;
import de.hanno.hpengine.engine.container.SimpleContainer;
import de.hanno.hpengine.engine.event.bus.EventBus;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.List;

public class EntityManager {

    private final Engine engine;
    private final EventBus eventbus;
    EntityContainer entities = new SimpleContainer();

    public EntityManager(Engine engine, EventBus eventbus) {
        this.engine = engine;
        this.eventbus = eventbus;
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
        entity.setIndex(entities.getEntities().indexOf(entity));
    }

    public void add(@NotNull List<Entity> entities) {
        for(Entity entity: entities) {
            add(entity);
        }
    }
}
