package container;

import camera.Camera;
import engine.model.Entity;
import renderer.Renderer;
import shader.Program;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class SimpleContainer implements EntitiesContainer {

    private Set<Entity> entities = new CopyOnWriteArraySet<>();
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
    }

    @Override
    public void insert(List<Entity> toDispatch) {
        entities.addAll(toDispatch);
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
        List<Entity> result = new ArrayList<>();
        result.addAll(entities);
        return result;
    }

    @Override
    public int getEntityCount() {
        return entities.size();
    }

    @Override
    public boolean removeEntity(Entity entity) {
        return entities.remove(entity);
    }
}
