package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.event.EntitySelectedEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.RenderExtract;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;

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
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {

        Engine engine = Engine.getInstance();
        if (engine.PICKING_CLICK == 1) {
            OpenGLContext.getInstance().readBuffer(4);

            floatBuffer.rewind();
            Vector2f ratio = new Vector2f(Float.valueOf(Config.WIDTH) / Float.valueOf(Engine.getInstance().getFrame().getWidth()),
                    Float.valueOf(Config.HEIGHT) / Float.valueOf(Engine.getInstance().getFrame().getHeight()));
            int adjustedX = (int) (Mouse.getX() * ratio.x);
            int adjustedY = (int) (Mouse.getY() * ratio.y);
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
            Logger.getGlobal().info("Picked: " + adjustedX + " : " + adjustedY);
            try {
                int componentIndex = 3; // alpha de.hanno.hpengine.component
                engine.getScene().getEntities().parallelStream().forEach(e -> {
                    e.setSelected(false);
                });
                Entity entity = engine.getScene().getEntities().stream().filter(e -> e.hasComponent(ModelComponent.class)).collect(Collectors.toList()).get((int) floatBuffer.get(componentIndex));
                entity.setSelected(true);
                Engine.getEventBus().post(new EntitySelectedEvent(entity));
            } catch (Exception e) {
                e.printStackTrace();
            }
            engine.PICKING_CLICK = 0;
        }
    }
}
