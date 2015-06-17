package component;

import engine.World;
import engine.model.Entity;
import renderer.Renderer;

import java.io.Serializable;

public abstract class BaseComponent implements Component, Serializable {

    transient protected World world;

    private Entity entity;

    transient private Renderer renderer;

    public Class getIdentifier() { return BaseComponent.class; }

    public void init(World world) {
        setWorld(world);
        setRenderer(world.getRenderer());
    }


    public Renderer getRenderer() {
        return renderer;
    }

    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public void setEntity(Entity entity) {
        this.entity = entity;
    }
}
