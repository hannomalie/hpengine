package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
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

    public DrawLinesExtension() throws Exception {
        identityMatrix44Buffer = new SimpleTransform().getTransformationBuffer();
        linesProgram = Engine.getInstance().getProgramFactory().getProgramFromFileNames("mvp_vertex.glsl", "firstpass_ambient_color_fragment.glsl", new Defines());
    }

    @Override
    public void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {

        if(Config.getInstance().isDrawBoundingVolumes() || Config.getInstance().isDrawCameras()) {

            GraphicsContext context = GraphicsContext.getInstance();
            context.disable(CULL_FACE);
            context.depthMask(false);

            linesProgram.use();
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            linesProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
            linesProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        }

        if(Config.getInstance().isDrawBoundingVolumes()) {

            renderBatches(renderState.getRenderBatchesStatic());
            renderBatches(renderState.getRenderBatchesAnimated());
            firstPassResult.linesDrawn += Engine.getInstance().getRenderer().drawLines(linesProgram);

//            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
//            Engine.getInstance().getSceneManager().getScene().getEntitiesContainer().drawDebug(Renderer.getInstance(), renderState.camera, linesProgram);

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

            Engine.getInstance().getRenderer().batchLine(new Vector3f(0,0,0), new Vector3f(15,0,0));
            Engine.getInstance().getRenderer().batchLine(new Vector3f(0,0,0), new Vector3f(0,15,0));
            Engine.getInstance().getRenderer().batchLine(new Vector3f(0,0,0), new Vector3f(0,0,15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
            int linesDrawn = Engine.getInstance().getRenderer().drawLines(linesProgram);

            Engine.getInstance().getRenderer().batchLine(new Vector3f(0,0,0), renderState.camera.getRightDirection().mul(15));
            Engine.getInstance().getRenderer().batchLine(new Vector3f(0,0,0), renderState.camera.getUpDirection().mul(15));
            Engine.getInstance().getRenderer().batchLine(new Vector3f(0,0,0), renderState.camera.getViewDirection().mul(15));
            linesProgram.setUniform("diffuseColor", new Vector3f(1,1,0));
            linesDrawn += Engine.getInstance().getRenderer().drawLines(linesProgram);
//            firstPassResult.linesDrawn += linesDrawn;


            Engine.getInstance().getPhysicsFactory().debugDrawWorld();
            firstPassResult.linesDrawn += Engine.getInstance().getRenderer().drawLines(linesProgram);
        }
        if(Config.getInstance().isDrawCameras()) {
//            TODO: Use renderstate somehow?
            List<Entity> entities = Engine.getInstance().getSceneManager().getScene().getEntities();
            for(int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                if(entity instanceof Camera) {
                    Camera camera = (Camera) entity;
                    Vector3f[] corners = camera.getFrustumCorners();
                    Engine.getInstance().getRenderer().batchLine(corners[0], corners[1]);
                    Engine.getInstance().getRenderer().batchLine(corners[1], corners[2]);
                    Engine.getInstance().getRenderer().batchLine(corners[2], corners[3]);
                    Engine.getInstance().getRenderer().batchLine(corners[3], corners[0]);

                    Engine.getInstance().getRenderer().batchLine(corners[4], corners[5]);
                    Engine.getInstance().getRenderer().batchLine(corners[5], corners[6]);
                    Engine.getInstance().getRenderer().batchLine(corners[6], corners[7]);
                    Engine.getInstance().getRenderer().batchLine(corners[7], corners[4]);

                    Engine.getInstance().getRenderer().batchLine(corners[0], corners[6]);
                    Engine.getInstance().getRenderer().batchLine(corners[1], corners[7]);
                    Engine.getInstance().getRenderer().batchLine(corners[2], corners[4]);
                    Engine.getInstance().getRenderer().batchLine(corners[3], corners[5]);
                }
            }
            firstPassResult.linesDrawn += Engine.getInstance().getRenderer().drawLines(linesProgram);
        }
    }

    private void renderBatches(List<RenderBatch> batches) {
        for (RenderBatch batch : batches) {
            if(Config.getInstance().isDrawBoundingVolumes()) {
                boolean renderAABBs = true;
                if(renderAABBs) {
                    batchAABBLines(batch.getMinWorld(), batch.getMaxWorld());
                    for(AABB minMax : batch.getInstanceMinMaxWorlds()) {
                        batchAABBLines(minMax.getMin(), minMax.getMax());
                    }
                } else {
                    float radius = batch.getBoundingSphereRadius();
                    Renderer renderer = Engine.getInstance().getRenderer();
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

    public static void batchAABBLines(Vector3f minWorld, Vector3f maxWorld) {
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }


        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z());
            Vector3f max = new Vector3f(maxWorld.x(), minWorld.y(), minWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
        {
            Vector3f min = new Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z());
            Vector3f max = new Vector3f(minWorld.x(), minWorld.y(), maxWorld.z());
            Engine.getInstance().getRenderer().batchLine(min, max);
        }
    }
}
