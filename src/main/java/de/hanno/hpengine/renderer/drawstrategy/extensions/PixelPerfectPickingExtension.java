package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.ApplicationFrame;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.input.Input;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.event.EntitySelectedEvent;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.nio.FloatBuffer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PixelPerfectPickingExtension implements RenderExtension {

    private final FloatBuffer floatBuffer;

    public PixelPerfectPickingExtension() {
        // 4 channels
        floatBuffer = BufferUtils.createFloatBuffer(4);
    }
    @Override
    public void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {

        Engine engine = Engine.getInstance();
        if (Input.PICKING_CLICK == 1) {
            GraphicsContext.getInstance().readBuffer(4);

            floatBuffer.rewind();
            Vector2f ratio = new Vector2f(Float.valueOf(Config.getInstance().getWidth()) / Float.valueOf(ApplicationFrame.WINDOW_WIDTH),
                    Float.valueOf(Config.getInstance().getHeight()) / Float.valueOf(ApplicationFrame.WINDOW_HEIGHT));
            int adjustedX = (int) (Mouse.getX() * ratio.x);
            int adjustedY = (int) (Mouse.getY() * ratio.y);
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
            Logger.getGlobal().info("Picked: " + adjustedX + " : " + adjustedY);
            try {
                int componentIndex = 3; // alpha component
                engine.getScene().getEntities().parallelStream().forEach(e -> {
                    e.setSelected(false);
                });
                int index = (int) floatBuffer.get(componentIndex);
                Entity entity = engine.getScene().getEntities().stream().filter(e -> e.hasComponent(ModelComponent.class)).collect(Collectors.toList()).get(index);
                entity.setSelected(true);
                Engine.getEventBus().post(new EntitySelectedEvent(entity));
            } catch (Exception e) {
                e.printStackTrace();
            }
            Input.PICKING_CLICK = 0;
        }
    }
}
