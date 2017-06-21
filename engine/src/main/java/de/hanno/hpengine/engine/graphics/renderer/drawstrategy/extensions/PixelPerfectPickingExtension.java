package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.input.Input;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.event.EntitySelectedEvent;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.joml.Vector2f;

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
            Vector2f ratio = new Vector2f((float) Config.getInstance().getWidth() / (float) GraphicsContext.getInstance().getCanvasWidth(),
                    (float) Config.getInstance().getHeight() / (float) GraphicsContext.getInstance().getCanvasHeight());
            int adjustedX = (int) (Input.getMouseX() * ratio.x);
            int adjustedY = (int) (Input.getMouseY() * ratio.y);
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
