package renderer.drawstrategy.extensions;

import engine.AppContext;
import engine.model.Entity;
import event.EntitySelectedEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.drawstrategy.FirstPassResult;

import java.nio.FloatBuffer;

public class PixelPerfectPickingExtension implements AfterFirstPassExtension {

    private final FloatBuffer floatBuffer;

    public PixelPerfectPickingExtension() {
        // 4 channels
        floatBuffer = BufferUtils.createFloatBuffer(4);
    }
    @Override
    public void run(RenderExtract renderExtract, FirstPassResult firstPassResult) {

        AppContext appContext = AppContext.getInstance();
        if (appContext.PICKING_CLICK == 1) {
            OpenGLContext.getInstance().readBuffer(4);

            floatBuffer.rewind();
            GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
            try {
                int componentIndex = 3; // alpha component
                appContext.getScene().getEntities().parallelStream().forEach(e -> {
                    e.setSelected(false);
                });
                Entity entity = appContext.getScene().getEntities().get((int) floatBuffer.get(componentIndex));
                entity.setSelected(true);
                AppContext.getEventBus().post(new EntitySelectedEvent(entity));
            } catch (Exception e) {
                e.printStackTrace();
            }
            appContext.PICKING_CLICK = 0;
        }
    }
}
