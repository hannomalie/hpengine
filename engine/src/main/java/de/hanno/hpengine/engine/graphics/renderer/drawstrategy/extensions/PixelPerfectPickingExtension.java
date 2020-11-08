package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.backend.OpenGl;
import de.hanno.hpengine.engine.event.MeshSelectedEvent;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.joml.Vector2f;

import java.nio.FloatBuffer;
import java.util.logging.Logger;

public class PixelPerfectPickingExtension implements RenderExtension<OpenGl> {

    private final FloatBuffer floatBuffer;

    public PixelPerfectPickingExtension() {
        // 4 channels
        floatBuffer = BufferUtils.createFloatBuffer(4);
    }
    @Override
    public void renderFirstPass(Backend<OpenGl> backend, GpuContext<OpenGl> gpuContext, FirstPassResult firstPassResult, RenderState renderState) {

        if (backend.getInput().getPickingClick() == 1) {
            gpuContext.readBuffer(4);

            floatBuffer.rewind();
//             TODO: This doesn't make sense anymore, does it?
            Vector2f ratio = new Vector2f((float) gpuContext.getWindow().getWidth() / (float) gpuContext.getWindow().getWidth(),
                    (float) gpuContext.getWindow().getHeight() / (float) gpuContext.getWindow().getHeight());
            int adjustedX = (int) (backend.getInput().getMouseX() * ratio.x);
            int adjustedY = (int) (backend.getInput().getMouseY() * ratio.y);
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
            Logger.getGlobal().info("Picked: " + adjustedX + " : " + adjustedY);
            try {
                int entityIndexComponentIndex = 0; // red component
                int meshIndexComponentIndex = 3; // alpha component
                int entityIndex = (int) floatBuffer.get(entityIndexComponentIndex);
                int meshIndex = (int) floatBuffer.get(meshIndexComponentIndex);
                backend.getEventBus().post(new MeshSelectedEvent(entityIndex, meshIndex));
            } catch (Exception e) {
                e.printStackTrace();
            }
            backend.getInput().setPickingClick(0);
        }
    }
}
