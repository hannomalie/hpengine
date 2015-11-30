package renderer.drawstrategy;

import engine.AppContext;

public interface DrawStrategy {
    DrawResult draw(AppContext appContext);
}
