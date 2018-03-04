package de.hanno.hpengine.engine.container;

import de.hanno.hpengine.engine.entity.Entity;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class SimpleContainer implements EntityContainer {

    private Set<Entity> entities = new LinkedHashSet<>();//new CopyOnWriteArraySet<>();
    List<Entity> result = new ArrayList<>();//new CopyOnWriteArrayList<>();

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

    @Override
    public void clear() {
        entities.clear();
    }

}
