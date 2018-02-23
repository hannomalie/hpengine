package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.shader.Program;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;

public class DrawLinesExtension implements RenderExtension {

    private final Program linesProgram;
    private final FloatBuffer identityMatrix44Buffer;
    private final Engine engine;

    public DrawLinesExtension(Engine engine) throws Exception {
        this.engine = engine;
        identityMatrix44Buffer = new SimpleTransform().getTransformationBuffer();
        linesProgram = this.engine.getProgramManager().getProgramFromFileNames("mvp_vertex.glsl", "firstpass_ambient_color_fragment.glsl", new Defines());
    }

    @Override
    public void renderFirstPass(Engine engine, GpuContext gpuContext, FirstPassResult firstPassResult, RenderState renderState) {

        if(Config.getInstance().isDrawBoundingVolumes() || Config.getInstance().isDrawCameras()) {

            GpuContext context = engine.getGpuContext();
            context.disable(CULL_FACE);
            context.depthMask(false);

            linesProgram.use();
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            linesProgram.setUniformAsMatrix4("viewMatrix", renderState.getCamera().getViewMatrixAsBuffer());
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderState.getCamera().getProjectionMatrixAsBuffer());
        }

        if(Config.getInstance().isDrawBoundingVolumes()) {

            renderBatches(renderState.getRenderBatchesStatic());
            renderBatches(renderState.getRenderBatchesAnimated());
            firstPassResult.linesDrawn += engine.getRenderer().drawLines(linesProgram);

//            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
//            engine.getSceneManager().getScene().getEntityManager().drawDebug(Renderer.getInstance(), renderState.getCamera(), linesProgram);

//            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
//            int max = 500;
//            for(int x = -max; x < max; x+=25) {
//                for(int y = -max; y < max; y+=25) {
//                    Renderer.getInstance().batchLine(new Vector3f(x,y,max), new Vector3f(x,y,-max));
//                }
//                for(int z = -max; z < max; z+=25) {
//                    Renderer.getInstance().batchLine(new Vector3f(x,max,z), new Vector3f(x,-max,z));
//                }
//            }

            engine.getRenderer().batchLine(new Vector3f(0,0,0), new Vector3f(15,0,0));
            engine.getRenderer().batchLine(new Vector3f(0,0,0), new Vector3f(0,15,0));
            engine.getRenderer().batchLine(new Vector3f(0,0,0), new Vector3f(0,0,15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
            int linesDrawn = engine.getRenderer().drawLines(linesProgram);

            engine.getRenderer().batchLine(new Vector3f(0,0,0), renderState.getCamera().getRightDirection().mul(15));
            engine.getRenderer().batchLine(new Vector3f(0,0,0), renderState.getCamera().getUpDirection().mul(15));
            engine.getRenderer().batchLine(new Vector3f(0,0,0), renderState.getCamera().getViewDirection().mul(15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,1,0));
            linesDrawn += engine.getRenderer().drawLines(linesProgram);
//            firstPassResult.linesDrawn += linesDrawn;


            engine.getPhysicsManager().debugDrawWorld();
            firstPassResult.linesDrawn += engine.getRenderer().drawLines(linesProgram);
        }
        if(Config.getInstance().isDrawCameras()) {
//            TODO: Use renderstate somehow?
            List<Camera> components = engine.getScene().getCameraComponentSystem().getComponents();
            for(int i = 0; i < components.size(); i++) {
                Camera camera = components.get(i);
                if(camera.equals(engine.getSceneManager().getActiveCamera())) { continue; }
                Vector3f[] corners = camera.getFrustumCorners();
                engine.getRenderer().batchLine(corners[0], corners[1]);
                engine.getRenderer().batchLine(corners[1], corners[2]);
                engine.getRenderer().batchLine(corners[2], corners[3]);
                engine.getRenderer().batchLine(corners[3], corners[0]);

                engine.getRenderer().batchLine(corners[4], corners[5]);
                engine.getRenderer().batchLine(corners[5], corners[6]);
                engine.getRenderer().batchLine(corners[6], corners[7]);
                engine.getRenderer().batchLine(corners[7], corners[4]);

                engine.getRenderer().batchLine(corners[0], corners[6]);
                engine.getRenderer().batchLine(corners[1], corners[7]);
                engine.getRenderer().batchLine(corners[2], corners[4]);
                engine.getRenderer().batchLine(corners[3], corners[5]);
            }
            firstPassResult.linesDrawn += engine.getRenderer().drawLines(linesProgram);
        }
    }

    private void renderBatches(List<RenderBatch> batches) {
        Renderer renderer = engine.getRenderer();

        for (RenderBatch batch : batches) {
            if(Config.getInstance().isDrawBoundingVolumes()) {
                boolean renderAABBs = true;
                if(renderAABBs) {
                    batchAABBLines(renderer, batch.getMinWorld(), batch.getMaxWorld());
                    for(AABB minMax : batch.getInstanceMinMaxWorlds()) {
                        batchAABBLines(renderer, minMax.getMin(), minMax.getMax());
                    }
                } else {
                    float radius = batch.getBoundingSphereRadius();
                    Vector3f center = batch.getCenterWorld();
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(-radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));

                    renderer.batchLine(new Vector3f(center).add(new Vector3f(-radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(-radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(radius, 0, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));

                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(-radius, 0, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, 0, -radius)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, radius, 0)));
                    renderer.batchLine(new Vector3f(center).add(new Vector3f(0, -radius, 0)), new Vector3f(center).add(new Vector3f(0, -radius, 0)));
                }

            }
        }
    }

    public static void batchAABBLines(Renderer renderer, Vector3f minWorld, Vector3f maxWorld) {
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }


        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            renderer.batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            renderer.batchLine(min, max);
        }
    }
}
