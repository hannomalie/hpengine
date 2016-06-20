package octree;

import camera.Camera;
import engine.model.Entity;
import renderer.Renderer;
import shader.Program;

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
