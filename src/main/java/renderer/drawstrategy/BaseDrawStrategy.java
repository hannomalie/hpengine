package renderer.drawstrategy;

import renderer.Renderer;

public abstract class BaseDrawStrategy implements DrawStrategy {
    protected final Renderer renderer;

    public BaseDrawStrategy(Renderer renderer) {
        this.renderer = renderer;
    }
}
