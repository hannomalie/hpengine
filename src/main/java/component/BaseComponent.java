package component;

import engine.World;
import engine.model.Entity;
import renderer.Renderer;

import java.io.Serializable;

public abstract class BaseComponent implements Component, Serializable {
    private static final long serialVersionUID = -224913983270697337L;

    transient protected World world;
    transient private Renderer renderer;

    private Entity entity;

    public String getIdentifier() { return this.getClass() + " " + System.currentTimeMillis(); }

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
