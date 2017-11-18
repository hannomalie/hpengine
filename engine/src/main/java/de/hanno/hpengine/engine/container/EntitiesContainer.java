package de.hanno.hpengine.engine.container;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.shader.Program;

import java.util.List;

public interface EntitiesContainer {
    void init();

    boolean isInitialized();

    void insert(Entity entity);

    void insert(List<Entity> entities);

    List<Entity> getVisible(Camera camera);

    void drawDebug(Renderer renderer, Camera camera, Program program);

    List<Entity> getEntities();

    int getEntityCount();

    boolean remove(Entity entity);
}
