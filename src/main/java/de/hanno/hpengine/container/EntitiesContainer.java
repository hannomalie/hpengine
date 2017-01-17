package de.hanno.hpengine.container;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.shader.Program;

import java.util.List;

public interface EntitiesContainer {
    void init();

    boolean isInitialized();

    void insert(Entity entity);

    void insert(List<Entity> toDispatch);

    List<Entity> getVisible(Camera camera);

    void drawDebug(Renderer renderer, Camera camera, Program program);

    List<Entity> getEntities();

    int getEntityCount();

    boolean removeEntity(Entity entity);
}
