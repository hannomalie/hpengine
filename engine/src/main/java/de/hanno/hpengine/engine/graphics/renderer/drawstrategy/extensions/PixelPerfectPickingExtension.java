package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.GpuContext;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.EntitySelectedEvent;
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
    public void renderFirstPass(Engine engine, GpuContext gpuContext, FirstPassResult firstPassResult, RenderState renderState) {

        if (engine.getInput().getPickingClick() == 1) {
            gpuContext.readBuffer(4);

            floatBuffer.rewind();
            Vector2f ratio = new Vector2f((float) Config.getInstance().getWidth() / (float) gpuContext.getCanvasWidth(),
                    (float) Config.getInstance().getHeight() / (float) gpuContext.getCanvasHeight());
            int adjustedX = (int) (engine.getInput().getMouseX() * ratio.x);
            int adjustedY = (int) (engine.getInput().getMouseY() * ratio.y);
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
            Logger.getGlobal().info("Picked: " + adjustedX + " : " + adjustedY);
            try {
                int componentIndex = 3; // alpha component
                engine.getSceneManager().getScene().getEntities().parallelStream().forEach(e -> {
                    e.setSelected(false);
                });
                int index = (int) floatBuffer.get(componentIndex);
                Entity entity = engine.getSceneManager().getScene().getEntities().stream().filter(e -> e.hasComponent(ModelComponent.class)).collect(Collectors.toList()).get(index);
                entity.setSelected(true);
                engine.getEventBus().post(new EntitySelectedEvent(entity));
            } catch (Exception e) {
                e.printStackTrace();
            }
            engine.getInput().setPickingClick(0);
        }
    }
}
