package de.hanno.hpengine.container;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.shader.Program;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class SimpleContainer implements EntitiesContainer {

    private Set<Entity> entities = new CopyOnWriteArraySet<>();
    List<Entity> result = new CopyOnWriteArrayList<>();
    private boolean initialized;

    @Override
    public void init() {
        entities = new CopyOnWriteArraySet<>();
        initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void insert(Entity entity) {
        entities.add(entity);
        result = Collections.unmodifiableList(new ArrayList<>(entities));
    }

    @Override
    public void insert(List<Entity> toDispatch) {
        entities.addAll(toDispatch);
        result = Collections.unmodifiableList(new ArrayList<>(entities));
    }

    @Override
    public List<Entity> getVisible(Camera camera) {
        return entities.stream().filter(e -> e.isInFrustum(camera)).collect(Collectors.toList());
    }

    @Override
    public void drawDebug(Renderer renderer, Camera camera, Program program) {

    }

    @Override
    public List<Entity> getEntities() {
        return result;
    }

    @Override
    public int getEntityCount() {
        return entities.size();
    }

    @Override
    public boolean removeEntity(Entity entity) {
        boolean remove = entities.remove(entity);
        result = Collections.unmodifiableList(result);
        return remove;
    }
}
