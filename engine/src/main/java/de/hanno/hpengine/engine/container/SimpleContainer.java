package de.hanno.hpengine.engine.container;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.model.Entity;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class SimpleContainer implements EntityManager {

    private Set<Entity> entities = new CopyOnWriteArraySet<>();
    List<Entity> result = new CopyOnWriteArrayList<>();

    @Override
    public void add(Entity entity) {
        entities.add(entity);
        result = Collections.unmodifiableList(new ArrayList<>(entities));
    }

    @Override
    public void add(List<Entity> entities) {
        this.entities.addAll(entities);
        result = Collections.unmodifiableList(new ArrayList<>(this.entities));
    }

    @Override
    public List<Entity> getEntities() {
        return result;
    }

}
