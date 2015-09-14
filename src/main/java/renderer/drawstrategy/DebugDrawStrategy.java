package renderer.drawstrategy;

import camera.Camera;
import com.bulletphysics.dynamics.DynamicsWorld;
import component.ModelComponent;
import config.Config;
import engine.Transform;
import engine.AppContext;
import engine.model.Entity;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.Renderer;
import renderer.constants.GlCap;
import renderer.light.AreaLight;
import renderer.light.LightFactory;
import renderer.light.PointLight;
import renderer.light.TubeLight;
import shader.Program;
import texture.CubeMap;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static renderer.constants.GlCap.BLEND;
import static renderer.constants.GlCap.DEPTH_TEST;

public class DebugDrawStrategy extends SimpleDrawStrategy {

    private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

    protected Program linesProgram;

    public DebugDrawStrategy(Renderer renderer) {
        super(renderer);
        linesProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");

        Transform transform = new Transform();
        transform.init();
        identityMatrixBuffer = transform.getTransformationBuffer();
    }

    @Override
    public void draw(AppContext appContext) {
        LightFactory lightFactory = appContext.getRenderer().getLightFactory();
        drawDebug(appContext.getActiveCamera(), appContext, appContext.getPhysicsFactory().getDynamicsWorld(),
                appContext.getScene().getOctree(), appContext.getScene().getEntities(),
                appContext.getScene().getPointLights(), appContext.getScene().getTubeLights(),
                appContext.getInstance().getScene().getAreaLights(), appContext.getRenderer().getEnvironmentMap());
    }


    public void drawDebug(Camera camera, AppContext appContext, DynamicsWorld dynamicsWorld, Octree octree, List<Entity> entities, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {
        GBuffer gBuffer = renderer.getGBuffer();

        renderer.getLightFactory().renderAreaLightShadowMaps(octree);

        gBuffer.use(true);
        openGLContext.disable(DEPTH_TEST);
        openGLContext.disable(BLEND);

        linesProgram.use();
        linesProgram.setUniform("screenWidth", (float) Config.WIDTH);
        linesProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        linesProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        linesProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        linesProgram.setUniform("eyePosition", camera.getPosition());

        if(Config.DRAWSCENE_ENABLED) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,0,1));
            List<Entity> visibleEntities = new ArrayList<>();
            if (Config.useFrustumCulling) {
                visibleEntities.addAll(octree.getVisible(camera));
                for (int i = 0; i < visibleEntities.size(); i++) {
                    if (!visibleEntities.get(i).isInFrustum(camera)) {
                        visibleEntities.remove(i);
                    }
                }
            } else {
                visibleEntities.addAll(octree.getEntities());
            }

            for (Entity entity : visibleEntities) {
                entity.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
                    modelComponent.drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
                });
            }
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            for (Entity entity : visibleEntities) {
                linesProgram.setUniformAsMatrix4("modelMatrix", entity.getModelMatrixAsBuffer());
                renderer.batchLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_VIEW).scale(1.5f), null));
                renderer.batchLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_RIGHT).scale(1.5f), null));
                renderer.batchLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_UP).scale(1.5f), null));
            }
            renderer.drawLines(linesProgram);
            // draw coord system for entity
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,1));
            for (Entity entity : visibleEntities) {
                Vector4f view = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_VIEW.x, Transform.WORLD_VIEW.y, Transform.WORLD_VIEW.z, 0.0f), null);
                Vector4f right = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_RIGHT.x, Transform.WORLD_RIGHT.y, Transform.WORLD_RIGHT.z, 0.0f), null);
                Vector4f up = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_UP.x, Transform.WORLD_UP.y, Transform.WORLD_UP.z, 0.0f), null);

                renderer.batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(view.x, view.y, view.z), null).scale(1));
                renderer.batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(right.x, right.y, right.z), null).scale(1));
                renderer.batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(up.x, up.y, up.z), null).scale(1));
            }
            renderer.drawLines(linesProgram);
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
            for (Entity entity : visibleEntities) {
                linesProgram.setUniformAsMatrix4("modelMatrix", entity.getModelMatrixAsBuffer());
                renderer.batchVector(entity.getViewDirection(), 0.1f);
                renderer.drawLines(linesProgram);
            }
            linesProgram.setUniform("diffuseColor", new Vector3f(0.5f,0.5f,0));
            for (Entity entity : visibleEntities) {
                Vector4f view = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getViewDirection().x, entity.getViewDirection().y, entity.getViewDirection().z, 0.0f), null);
                Vector4f right = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getRightDirection().x, entity.getRightDirection().y, entity.getRightDirection().z, 0.0f), null);
                Vector4f up = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getUpDirection().x, entity.getUpDirection().y, entity.getUpDirection().z, 0.0f), null);

                renderer.batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(view.x, view.y, view.z), null).scale(1));
                renderer.batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(right.x, right.y, right.z), null).scale(1));
                renderer.batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(up.x, up.y, up.z), null).scale(1));
                renderer.drawLines(linesProgram);
            }
        }

        if (Config.DRAWLIGHTS_ENABLED) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,1));
            for (Entity entity : pointLights) {
                entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
            }
            for (Entity entity : tubeLights) {
                entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
            }
            for (AreaLight entity : areaLights) {
                entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());

                renderer.batchLine(new Vector3f(), (Vector3f) new Vector3f(Transform.WORLD_VIEW).scale(0.1f));
                renderer.batchLine(new Vector3f(), (Vector3f) new Vector3f(Transform.WORLD_RIGHT).scale(0.1f));
                renderer.batchLine(new Vector3f(), (Vector3f) new Vector3f(Transform.WORLD_UP).scale(0.1f));
                linesProgram.setUniform("diffuseColor", new Vector3f(1,0,1));
                linesProgram.setUniformAsMatrix4("modelMatrix", entity.getModelMatrixAsBuffer());
                renderer.drawLines(linesProgram);
            }

            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,0));
            renderer.batchLine(new Vector3f(), appContext.getScene().getDirectionalLight().getCamera().getWorldPosition());
            renderer.batchLine(appContext.getScene().getDirectionalLight().getCamera().getWorldPosition(),
                               Vector3f.add(appContext.getScene().getDirectionalLight().getCamera().getWorldPosition(),
                                       (Vector3f) new Vector3f(appContext.getScene().getDirectionalLight().getCamera().getViewDirection()).scale(10f),
                                       null));
            renderer.drawLines(linesProgram);
        }

        if (Octree.DRAW_LINES) {
            linesProgram.setUniform("diffuseColor", new Vector3f(1,1,1));
            octree.drawDebug(renderer, camera, linesProgram);
        }

        if (Config.DRAW_PROBES) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
            renderer.getEnvironmentProbeFactory().drawDebug(linesProgram, octree);
        }

        linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
        renderer.batchLine(new Vector3f(-1000, 0, 0), new Vector3f(1000, 0, 0));
        renderer.batchLine(new Vector3f(0, -1000, 0), new Vector3f(0, 1000, 0));
        renderer.batchLine(new Vector3f(0, 0, -1000), new Vector3f(0, 0, 1000));
//        renderer.batchLine(new Vector3f(), (Vector3f) camera.getViewDirection().scale(15));
//        renderer.batchLine(new Vector3f(), (Vector3f) camera.getRightDirection().scale(15));
//        renderer.batchLine(new Vector3f(), (Vector3f) camera.getUpDirection().scale(15));

        renderer.drawLines(linesProgram);
        dynamicsWorld.debugDrawWorld();
        renderer.drawLines(linesProgram);

        openGLContext.depthMask(false);
        openGLContext.disable(DEPTH_TEST);
        ////////////////////

        drawSecondPass(camera, appContext.getScene().getDirectionalLight(), pointLights, tubeLights, areaLights, cubeMap);


        openGLContext.viewPort(0,0, Config.WIDTH, Config.HEIGHT);
        openGLContext.clearDepthAndColorBuffer();

        renderer.getOpenGLContext().disable(DEPTH_TEST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        renderer.drawToQuad(renderer.getGBuffer().getPositionMap()); // the first color attachment
        renderer.getOpenGLContext().enable(DEPTH_TEST);
    }
}
