package renderer.drawstrategy;

import camera.Camera;
import com.bulletphysics.dynamics.DynamicsWorld;
import component.ModelComponent;
import config.Config;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.light.AreaLight;
import renderer.light.PointLight;
import renderer.light.TubeLight;
import scene.EnvironmentProbeFactory;
import texture.CubeMap;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static renderer.constants.GlCap.BLEND;
import static renderer.constants.GlCap.DEPTH_TEST;

public class ProbeDebugDrawStrategy extends DebugDrawStrategy {

    private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

    public ProbeDebugDrawStrategy(Renderer renderer) throws Exception {
        super();
    }

    public void drawDebug(Camera camera, AppContext appContext, DynamicsWorld dynamicsWorld, Octree octree, List<Entity> entities, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {
        ///////////// firstpass
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();

        gBuffer.use(true);
        OpenGLContext.getInstance().disable(DEPTH_TEST);
        OpenGLContext.getInstance().disable(BLEND);

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
                Renderer.getInstance().batchLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_VIEW).scale(1.5f), null));
                Renderer.getInstance().batchLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_RIGHT).scale(1.5f), null));
                Renderer.getInstance().batchLine(entity.getWorldPosition(), Vector3f.add(entity.getWorldPosition(), (Vector3f) new Vector3f(Transform.WORLD_UP).scale(1.5f), null));
            }
            Renderer.getInstance().drawLines(linesProgram);
            // draw coord system for entity
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
            linesProgram.setUniform("diffuseColor", new Vector3f(1,0,1));
            for (Entity entity : visibleEntities) {
                Vector4f view = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_VIEW.x, Transform.WORLD_VIEW.y, Transform.WORLD_VIEW.z, 0.0f), null);
                Vector4f right = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_RIGHT.x, Transform.WORLD_RIGHT.y, Transform.WORLD_RIGHT.z, 0.0f), null);
                Vector4f up = Matrix4f.transform(entity.getModelMatrix(), new Vector4f(Transform.WORLD_UP.x, Transform.WORLD_UP.y, Transform.WORLD_UP.z, 0.0f), null);

                Renderer.getInstance().batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(view.x, view.y, view.z), null).scale(1));
                Renderer.getInstance().batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(right.x, right.y, right.z), null).scale(1));
                Renderer.getInstance().batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(up.x, up.y, up.z), null).scale(1));
            }
            Renderer.getInstance().drawLines(linesProgram);
            linesProgram.setUniform("diffuseColor", new Vector3f(0.5f,0.5f,0));
            for (Entity entity : visibleEntities) {
                Vector4f view = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getViewDirection().x, entity.getViewDirection().y, entity.getViewDirection().z, 0.0f), null);
                Vector4f right = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getRightDirection().x, entity.getRightDirection().y, entity.getRightDirection().z, 0.0f), null);
                Vector4f up = Matrix4f.transform(entity.getViewMatrix(), new Vector4f(entity.getUpDirection().x, entity.getUpDirection().y, entity.getUpDirection().z, 0.0f), null);

                Renderer.getInstance().batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(view.x, view.y, view.z), null).scale(1));
                Renderer.getInstance().batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(right.x, right.y, right.z), null).scale(1));
                Renderer.getInstance().batchLine(entity.getWorldPosition(), (Vector3f) Vector3f.add(entity.getWorldPosition(), new Vector3f(up.x, up.y, up.z), null).scale(1));
            }
            Renderer.getInstance().drawLines(linesProgram);
        }

        linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
        if (Config.DRAWLIGHTS_ENABLED) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,1));
            for (Entity entity : pointLights) {
                entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
            }
            for (Entity entity : tubeLights) {
                entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
            }
        }

        if (Octree.DRAW_LINES) {
            linesProgram.setUniform("diffuseColor", new Vector3f(1,1,1));
            octree.drawDebug(Renderer.getInstance(), camera, linesProgram);
        }

        if (Config.DRAW_PROBES) {
            linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
            linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
            EnvironmentProbeFactory.getInstance().drawDebug(linesProgram, octree);
        }

        Renderer.getInstance().batchLine(new Vector3f(), new Vector3f(15, 0, 0));
        Renderer.getInstance().batchLine(new Vector3f(), new Vector3f(0, 15, 0));
        Renderer.getInstance().batchLine(new Vector3f(), new Vector3f(0, 0, -15));
        Renderer.getInstance().batchLine(new Vector3f(), (Vector3f) ((Vector3f) (camera.getViewDirection())).scale(15));
        Renderer.getInstance().batchLine(new Vector3f(), (Vector3f) ((Vector3f) (camera.getRightDirection())).scale(15));
        Renderer.getInstance().batchLine(new Vector3f(), (Vector3f) ((Vector3f) (camera.getUpDirection())).scale(15));
        dynamicsWorld.debugDrawWorld();
        Renderer.getInstance().drawLines(linesProgram);

        GL11.glDepthMask(false);
        OpenGLContext.getInstance().disable(DEPTH_TEST);
        ////////////////////

        drawSecondPass(camera, appContext.getScene().getDirectionalLight(), pointLights, tubeLights, areaLights, cubeMap);

        OpenGLContext.getInstance().viewPort(0, 0, Config.WIDTH, Config.HEIGHT);
        OpenGLContext.getInstance().clearDepthAndColorBuffer();

        OpenGLContext.getInstance().disable(DEPTH_TEST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        Renderer.getInstance().drawToQuad(Renderer.getInstance().getGBuffer().getPositionMap()); // the first color attachment
        OpenGLContext.getInstance().enable(DEPTH_TEST);
    }
}
