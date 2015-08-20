package engine.lifecycle;


import engine.World;
import engine.model.EntityFactory;
import renderer.material.MaterialFactory;

public interface LifeCycle {

    default void init(World world) { setWorld(world); }
    default void update(float seconds) { }
    @SuppressWarnings("unused")
    default void destroy(World world) { }

    void setWorld(World world);
    World getWorld();

    default MaterialFactory getMaterialFactory() {
        return getWorld().getRenderer().getMaterialFactory();
    }
    default EntityFactory getEntityFactory() {
        return getWorld().getEntityFactory();
    }
}
