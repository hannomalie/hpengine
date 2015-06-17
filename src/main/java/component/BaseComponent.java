package component;

import engine.World;
import renderer.Renderer;

import java.io.Serializable;

public abstract class BaseComponent implements Component, Serializable {

    transient protected World world;

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
}
