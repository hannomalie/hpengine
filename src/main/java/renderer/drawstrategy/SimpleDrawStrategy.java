package renderer.drawstrategy;

import camera.Camera;
import component.ModelComponent;
import config.Config;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import event.EntitySelectedEvent;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.*;
import renderer.constants.CullMode;
import renderer.constants.GlCap;
import renderer.constants.GlTextureTarget;
import renderer.light.*;
import renderer.rendertarget.RenderTarget;
import scene.AABB;
import scene.EnvironmentProbe;
import scene.EnvironmentProbeFactory;
import shader.ComputeShaderProgram;
import shader.Program;
import shader.ProgramFactory;
import texture.CubeMap;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static renderer.constants.BlendMode.*;
import static renderer.constants.BlendMode.Factor.*;
import static renderer.constants.CullMode.BACK;
import static renderer.constants.GlCap.BLEND;
import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;
import static renderer.constants.GlDepthFunc.LESS;
import static renderer.constants.GlTextureTarget.*;

public class SimpleDrawStrategy extends BaseDrawStrategy {
    public static volatile boolean USE_COMPUTESHADER_FOR_REFLECTIONS = false;
    public static volatile int IMPORTANCE_SAMPLE_COUNT = 8;

    private Program firstPassProgram;
    private Program depthPrePassProgram;
    private Program secondPassDirectionalProgram;
    private Program secondPassPointProgram;
    private Program secondPassTubeProgram;
    private Program secondPassAreaProgram;
    private Program combineProgram;
    private Program postProcessProgram;
    private Program instantRadiosityProgram;
    private Program aoScatteringProgram;
    private Program highZProgram;
    private Program reflectionProgram;
    private Program linesProgram;
    private Program probeFirstpassProgram;
    private ComputeShaderProgram tiledProbeLightingProgram;
    private ComputeShaderProgram tiledDirectLightingProgram;

    OpenGLContext openGLContext;

    public SimpleDrawStrategy(Renderer renderer) {
        super(renderer);
        ProgramFactory programFactory = renderer.getProgramFactory();
        firstPassProgram = programFactory.getProgram("first_pass_vertex.glsl", "first_pass_fragment.glsl");
        secondPassPointProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        secondPassTubeProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_tube_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        secondPassAreaProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_area_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        secondPassDirectionalProgram = programFactory.getProgram("second_pass_directional_vertex.glsl", "second_pass_directional_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        instantRadiosityProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_instant_radiosity_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);

        combineProgram = programFactory.getProgram("combine_pass_vertex.glsl", "combine_pass_fragment.glsl", DeferredRenderer.RENDERTOQUAD, false);
        postProcessProgram = programFactory.getProgram("passthrough_vertex.glsl", "postprocess_fragment.glsl", DeferredRenderer.RENDERTOQUAD, false);

        aoScatteringProgram = renderer.getProgramFactory().getProgram("passthrough_vertex.glsl", "scattering_ao_fragment.glsl");
        highZProgram = renderer.getProgramFactory().getProgram("passthrough_vertex.glsl", "highZ_fragment.glsl");
        reflectionProgram = renderer.getProgramFactory().getProgram("passthrough_vertex.glsl", "reflections_fragment.glsl");
        linesProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");
        probeFirstpassProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "probe_first_pass_fragment.glsl");
        depthPrePassProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "depth_prepass_fragment.glsl");
        tiledDirectLightingProgram = renderer.getProgramFactory().getComputeProgram("tiled_direct_lighting_compute.glsl");
        tiledProbeLightingProgram = renderer.getProgramFactory().getComputeProgram("tiled_probe_lighting_compute.glsl");

        openGLContext = renderer.getOpenGLContext();
    }

    public void draw(AppContext appContext) {
        draw(appContext.getActiveCamera(), appContext, appContext.getScene().getEntities());
    }

    public void draw(Camera camera, AppContext appContext, List<Entity> entities) {
        draw(appContext, null, appContext.getScene().getOctree(), camera, entities);
    }

    private void draw(AppContext appContext, RenderTarget target, Octree octree, Camera camera, List<Entity> entities) {
        LightFactory lightFactory = renderer.getLightFactory();
        EnvironmentProbeFactory environmentProbeFactory = renderer.getEnvironmentProbeFactory();
        DirectionalLight light = appContext.getScene().getDirectionalLight();

        GPUProfiler.start("First pass");
        drawFirstPass(appContext, camera, octree, appContext.getScene().getPointLights(), appContext.getScene().getTubeLights(), appContext.getScene().getAreaLights());
        GPUProfiler.end();

        if (!Config.DEBUGDRAW_PROBES) {
            environmentProbeFactory.drawAlternating(octree, camera, light, renderer.getFrameCount());
            renderer.executeRenderProbeCommands();
            GPUProfiler.start("Shadowmap pass");
//			if(light.hasMoved() || !octree.getEntities().parallelStream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty())
            {
                GPUProfiler.start("Directional shadowmap");
                light.drawShadowMap(octree);
                GPUProfiler.end();
            }
            lightFactory.renderAreaLightShadowMaps(octree);
            GPUProfiler.end();
            GPUProfiler.start("Second pass");
            drawSecondPass(camera, light, appContext.getScene().getPointLights(), appContext.getScene().getTubeLights(), appContext.getScene().getAreaLights(), renderer.getEnvironmentMap());
            GPUProfiler.end();
            renderer.getOpenGLContext().viewPort(0, 0, Config.WIDTH, Config.HEIGHT);
            renderer.getOpenGLContext().clearDepthAndColorBuffer();
            renderer.getOpenGLContext().disable(DEPTH_TEST);
            GPUProfiler.start("Combine pass");
            combinePass(target, light, camera);
            GPUProfiler.end();
        } else {
            renderer.getOpenGLContext().viewPort(0, 0, Config.WIDTH, Config.HEIGHT);
            renderer.getOpenGLContext().clearDepthAndColorBuffer();
            renderer.getOpenGLContext().disable(DEPTH_TEST);

            renderer.getOpenGLContext().bindFrameBuffer(0);
            renderer.drawToQuad(renderer.getGBuffer().getColorReflectivenessMap());
        }
    }

    public void drawFirstPass(AppContext appContext, Camera camera, Octree octree, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights) {
        openGLContext.enable(CULL_FACE);
        openGLContext.depthMask(true);
        renderer.getGBuffer().use(true);
        openGLContext.enable(DEPTH_TEST);
        openGLContext.depthFunc(LESS);
        openGLContext.disable(GlCap.BLEND);

        GPUProfiler.start("Culling");
        List<Entity> entities = new ArrayList<>();

        if (Config.useFrustumCulling) {
            entities = (octree.getVisible(camera));

            for (int i = 0; i < entities.size(); i++) {
                if (!entities.get(i).isInFrustum(camera)) {
                    entities.remove(i);
                }
            }
        } else {
            entities = (octree.getEntities());
        }
        GPUProfiler.end();

        GPUProfiler.start("Draw entities");

        if(Config.DRAWSCENE_ENABLED) {
//			GPUProfiler.start("Depth prepass");
            for (Entity entity : entities) {
                if(entity.getComponents().containsKey("ModelComponent")) {
                    ModelComponent.class.cast(entity.getComponents().get("ModelComponent")).draw(camera);
                }
//				entity.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//					modelComponent.draw(cameraEntity);
//				});
            }
        }
        GPUProfiler.end();

        if (Config.DRAWLIGHTS_ENABLED) {
            for (PointLight light : pointLights) {
                if (!light.isInFrustum(camera)) { continue;}
                light.drawAsMesh(camera);
            }
            for (TubeLight light : tubeLights) {
//				if (!light.isInFrustum(camera)) { continue;}
                light.drawAsMesh(camera);
            }
            for (AreaLight light : areaLights) {
//				if (!light.isInFrustum(camera)) { continue;}
                light.drawAsMesh(camera);
            }
            appContext.getScene().getDirectionalLight().drawAsMesh(camera);

//            renderer.batchLine(renderer.getLightFactory().getDirectionalLight().getWorldPosition(),
//                    renderer.getLightFactory().getDirectionalLight().getCamera().getWorldPosition());
//            renderer.drawLines(linesProgram);

        }

        if(Config.DEBUGDRAW_PROBES) {
            debugDrawProbes(camera);
            renderer.getEnvironmentProbeFactory().draw(octree);
        }
        openGLContext.enable(CULL_FACE);

        GPUProfiler.start("Generate Mipmaps of colormap");
        openGLContext.activeTexture(0);
        renderer.getTextureFactory().generateMipMaps(renderer.getGBuffer().getColorReflectivenessMap());
        GPUProfiler.end();

        if(appContext.PICKING_CLICK == 1) {
            openGLContext.readBuffer(4);

            FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(4); // 4 channels
            GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
            try {
                int componentIndex = 3; // alpha component
                appContext.getScene().getEntities().parallelStream().forEach(e -> { e.setSelected(false); });
                Entity entity = appContext.getScene().getEntities().get((int)floatBuffer.get(componentIndex));
                entity.setSelected(true);
                AppContext.getEventBus().post(new EntitySelectedEvent(entity));
            } catch (Exception e) {
                e.printStackTrace();
            }
            floatBuffer = null;
            appContext.PICKING_CLICK = 0;
        }
    }

    public void drawSecondPass(Camera camera, DirectionalLight directionalLight, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {
        renderer.getTextureFactory().generateMipMaps(directionalLight.getShadowMapId());

        Vector3f camPosition = camera.getPosition();
        Vector3f.add(camPosition, (Vector3f) camera.getViewDirection().scale(-camera.getNear()), camPosition);
        Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);

        GPUProfiler.start("Directional light");
        openGLContext.depthMask(false);
        openGLContext.enable(DEPTH_TEST);
        openGLContext.enable(BLEND);
        openGLContext.blendEquation(FUNC_ADD);
        openGLContext.blendFunc(ONE, ONE);

        GBuffer gBuffer = renderer.getGBuffer();
        gBuffer.getLightAccumulationBuffer().use(true);
//		laBuffer.resizeTextures();
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.getDepthBufferTexture());
        AppContext.getInstance().getRenderer().getOpenGLContext().clearColor(0, 0, 0, 0);
        AppContext.getInstance().getRenderer().getOpenGLContext().clearColorBuffer();

        GPUProfiler.start("Activate GBuffer textures");
        openGLContext.bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        openGLContext.bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        openGLContext.bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        openGLContext.bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        openGLContext.bindTexture(4, TEXTURE_CUBE_MAP, cubeMap.getTextureID());
        openGLContext.bindTexture(6, TEXTURE_2D, directionalLight.getShadowMapId());
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapWorldPositionId()); // world position
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getVisibilityMap());
        openGLContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).getTextureID());

        GPUProfiler.end();

        secondPassDirectionalProgram.use();
        secondPassDirectionalProgram.setUniform("eyePosition", camera.getWorldPosition());
        secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.AMBIENTOCCLUSION_RADIUS);
        secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.AMBIENTOCCLUSION_TOTAL_STRENGTH);
        secondPassDirectionalProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassDirectionalProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassDirectionalProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
        FloatBuffer viewMatrix = camera.getViewMatrixAsBuffer();
        secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        FloatBuffer projectionMatrix = camera.getProjectionMatrixAsBuffer();
        secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
        secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getCamera().getViewDirection());
//        System.out.println("Light View Direction: " + directionalLight.getViewDirection());
//        System.out.println("Cam View Direction: " + directionalLight.getCamera().getViewDirection());
        secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
        renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(secondPassDirectionalProgram);
        GPUProfiler.start("Draw fullscreen buffer");
        renderer.getFullscreenBuffer().draw();
        GPUProfiler.end();

        GPUProfiler.end();

        doPointLights(camera, pointLights, camPosition, viewMatrix, projectionMatrix);

        doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

        doAreaLights(areaLights, viewMatrix, projectionMatrix);

        doInstantRadiosity(directionalLight, viewMatrix, projectionMatrix);


        GPUProfiler.start("MipMap generation AO and light buffer");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        renderer.getTextureFactory().generateMipMaps(gBuffer.getLightAccumulationMapOneId());
        renderer.getTextureFactory().generateMipMaps(gBuffer.getAmbientOcclusionMapId());
        GPUProfiler.end();

        renderer.getOpenGLContext().disable(BLEND);

        gBuffer.getLightAccumulationBuffer().unuse();

        renderAOAndScattering(camera, viewMatrix, projectionMatrix, directionalLight);

        if (Config.USE_GI) {
            GL11.glDepthMask(false);
            renderer.getOpenGLContext().disable(DEPTH_TEST);
            renderer.getOpenGLContext().disable(BLEND);
            renderer.getOpenGLContext().cullFace(BACK);
            renderReflectionsAndAO(viewMatrix, projectionMatrix);
        } else {
            gBuffer.getReflectionBuffer().use(true);
            gBuffer.getReflectionBuffer().unuse();
        }
        GPUProfiler.start("Blurring");
//		renderer.blur2DTexture(halfScreenBuffer.getRenderedTexture(), 0, (int)(Config.WIDTH/2), (int)(Config.HEIGHT/2), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.WIDTH*SECONDPASSSCALE), (int)(Config.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.WIDTH*SECONDPASSSCALE), (int)(Config.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getAmbientOcclusionMapId(), (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);

        GL11.glCullFace(GL11.GL_BACK);
        GL11.glDepthFunc(GL11.GL_LESS);
        GPUProfiler.end();
    }

    private void doPointLights(Camera camera, List<PointLight> pointLights,
                               Vector3f camPosition, FloatBuffer viewMatrix,
                               FloatBuffer projectionMatrix) {

        if(pointLights.isEmpty()) { return; }

        GPUProfiler.start("Pointlights");
        secondPassPointProgram.use();

        GPUProfiler.start("Set shared uniforms");
//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
        secondPassPointProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassPointProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassPointProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
        secondPassPointProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        GPUProfiler.end();

        GPUProfiler.start("Draw lights");
        boolean firstLightDrawn = false;
        for (int i = 0 ; i < pointLights.size(); i++) {
            PointLight light = pointLights.get(i);
            if(!light.isInFrustum(camera)) {
                continue;
            }

            Vector3f distance = new Vector3f();
            Vector3f.sub(light.getPosition(), camPosition, distance); // <----- biggest Hack ever! TODO: Check where this fuckup with the cam goes on.... :(
            float lightRadius = light.getRadius();

            // camera is inside light
            if (distance.length() < lightRadius) {
                GL11.glCullFace(GL11.GL_FRONT);
                GL11.glDepthFunc(GL11.GL_GEQUAL);
            } else {
                // camera is outside light, cull back sides
                GL11.glCullFace(GL11.GL_BACK);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
            }

//			secondPassPointProgram.setUniform("currentLightIndex", i);
            secondPassPointProgram.setUniform("lightPosition", light.getPosition());
            secondPassPointProgram.setUniform("lightRadius", lightRadius);
            secondPassPointProgram.setUniform("lightDiffuse", light.getColor().x, light.getColor().y, light.getColor().z);

            if(firstLightDrawn) {
                light.drawAgain(secondPassPointProgram);
            } else {
                light.draw(secondPassPointProgram);
            }
            firstLightDrawn = true;
        }
        GPUProfiler.end();
        GPUProfiler.end();
    }
    private void doTubeLights(List<TubeLight> tubeLights,
                              Vector4f camPositionV4, FloatBuffer viewMatrix,
                              FloatBuffer projectionMatrix) {


        if(tubeLights.isEmpty()) { return; }

        secondPassTubeProgram.use();
        secondPassTubeProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassTubeProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassTubeProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
        secondPassTubeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassTubeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        for (TubeLight tubeLight : tubeLights) {
            boolean camInsideLightVolume = new AABB(tubeLight.getPosition(), tubeLight.getScale().x, tubeLight.getScale().y, tubeLight.getScale().z).contains(camPositionV4);
            if (camInsideLightVolume) {
                GL11.glCullFace(GL11.GL_FRONT);
                GL11.glDepthFunc(GL11.GL_GEQUAL);
            } else {
                GL11.glCullFace(GL11.GL_BACK);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
            }
//			System.out.println("START " + tubeLight.getStart());
//			System.out.println("AT " + tubeLight.getPosition());
//			System.out.println("END " + tubeLight.getEnd());
//			System.out.println("RADIUS " + tubeLight.getRadius());
//			System.out.println("SCALE " + tubeLight.getScale());
            secondPassTubeProgram.setUniform("lightPosition", tubeLight.getPosition());
            secondPassTubeProgram.setUniform("lightStart", tubeLight.getStart());
            secondPassTubeProgram.setUniform("lightEnd", tubeLight.getEnd());
            secondPassTubeProgram.setUniform("lightOuterLeft", tubeLight.getOuterLeft());
            secondPassTubeProgram.setUniform("lightOuterRight", tubeLight.getOuterRight());
            secondPassTubeProgram.setUniform("lightRadius", tubeLight.getRadius());
            secondPassTubeProgram.setUniform("lightLength", tubeLight.getLength());
            secondPassTubeProgram.setUniform("lightDiffuse", tubeLight.getColor());
            tubeLight.draw(secondPassTubeProgram);
        }
    }

    private void doAreaLights(List<AreaLight> areaLights, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {

        renderer.getOpenGLContext().disable(CULL_FACE);
        renderer.getOpenGLContext().disable(DEPTH_TEST);
        if(areaLights.isEmpty()) { return; }

        GPUProfiler.start("Area lights: " + areaLights.size());

        secondPassAreaProgram.use();
        secondPassAreaProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassAreaProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassAreaProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
        secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        for (AreaLight areaLight : areaLights) {
//			boolean camInsideLightVolume = new AABB(areaLight.getPosition(), 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
//			if (camInsideLightVolume) {
//				GL11.glCullFace(GL11.GL_FRONT);
//				GL11.glDepthFunc(GL11.GL_GEQUAL);
//			} else {
//				GL11.glCullFace(GL11.GL_BACK);
//				GL11.glDepthFunc(GL11.GL_LEQUAL);
//			}
            secondPassAreaProgram.setUniform("lightPosition", areaLight.getPosition());
            secondPassAreaProgram.setUniform("lightRightDirection", areaLight.getRightDirection());
            secondPassAreaProgram.setUniform("lightViewDirection", areaLight.getViewDirection());
            secondPassAreaProgram.setUniform("lightUpDirection", areaLight.getUpDirection());
            secondPassAreaProgram.setUniform("lightWidth", areaLight.getWidth());
            secondPassAreaProgram.setUniform("lightHeight", areaLight.getHeight());
            secondPassAreaProgram.setUniform("lightRange", areaLight.getRange());
            secondPassAreaProgram.setUniform("lightDiffuse", areaLight.getColor());
            secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", areaLight.getViewProjectionMatrixAsBuffer());

            // TODO: Add textures to arealights
//			try {
//				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//				Texture lightTexture = renderer.getTextureFactory().getTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
            AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(9, GlTextureTarget.TEXTURE_2D, renderer.getLightFactory().getDepthMapForAreaLight(areaLight));
            renderer.getFullscreenBuffer().draw();
//			areaLight.getVertexBuffer().drawDebug();
        }

        GPUProfiler.end();
    }

    private void doInstantRadiosity(DirectionalLight directionalLight, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
        if(Config.useInstantRadiosity) {
            GPUProfiler.start("Instant Radiosity");
            AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(6, TEXTURE_2D, directionalLight.getShadowMapId());
            AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(7, TEXTURE_2D, directionalLight.getShadowMapWorldPositionId());
            AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(8, TEXTURE_2D, directionalLight.getShadowMapColorMapId());
            instantRadiosityProgram.use();
            instantRadiosityProgram.setUniform("screenWidth", (float) Config.WIDTH);
            instantRadiosityProgram.setUniform("screenHeight", (float) Config.HEIGHT);
            instantRadiosityProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
            instantRadiosityProgram.setUniform("lightDiffuse", directionalLight.getColor());
            instantRadiosityProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
            instantRadiosityProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
            renderer.getOpenGLContext().disable(CULL_FACE);
            renderer.getOpenGLContext().disable(DEPTH_TEST);
            renderer.getFullscreenBuffer().draw();
            GPUProfiler.end();
        }
    }

    private void renderAOAndScattering(Entity cameraEntity, FloatBuffer viewMatrix, FloatBuffer projectionMatrix, DirectionalLight directionalLight) {
        if(!Config.useAmbientOcclusion && !Config.SCATTERING) { return; }
        GBuffer gBuffer = renderer.getGBuffer();
        GPUProfiler.start("Scattering and AO");
        renderer.getOpenGLContext().disable(DEPTH_TEST);
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(6, TEXTURE_2D, directionalLight.getShadowMapId());
        renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind(8);

        gBuffer.getHalfScreenBuffer().use(true);
//		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
        aoScatteringProgram.use();
        aoScatteringProgram.setUniform("eyePosition", cameraEntity.getPosition());
        aoScatteringProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
        aoScatteringProgram.setUniform("ambientOcclusionRadius", Config.AMBIENTOCCLUSION_RADIUS);
        aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", Config.AMBIENTOCCLUSION_TOTAL_STRENGTH);
        aoScatteringProgram.setUniform("screenWidth", (float) Config.WIDTH/2);
        aoScatteringProgram.setUniform("screenHeight", (float) Config.HEIGHT/2);
        aoScatteringProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        aoScatteringProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
        aoScatteringProgram.setUniform("lightDirection", directionalLight.getViewDirection());
        aoScatteringProgram.setUniform("lightDiffuse", directionalLight.getColor());
        aoScatteringProgram.setUniform("scatterFactor", directionalLight.getScatterFactor());
        renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(aoScatteringProgram);
        renderer.getFullscreenBuffer().draw();
        renderer.getOpenGLContext().enable(DEPTH_TEST);
        renderer.getTextureFactory().generateMipMaps(gBuffer.getHalfScreenBuffer().getRenderedTexture());
        GPUProfiler.end();
    }

    private void renderReflectionsAndAO(FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
        GPUProfiler.start("Reflections and AO");
        GBuffer gBuffer = renderer.getGBuffer();
        RenderTarget reflectionBuffer = gBuffer.getReflectionBuffer();

        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(4, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(5, TEXTURE_2D, gBuffer.getFinalMap());
        renderer.getEnvironmentMap().bind(6);
//        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//        reflectionBuffer.getRenderedTexture(0);
        renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind(8);
        renderer.getEnvironmentMap().bind(9);
        renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(0).bind(10);
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(11, TEXTURE_2D, reflectionBuffer.getRenderedTexture());

        int copyTextureId = GL11.glGenTextures();
        renderer.getOpenGLContext().bindTexture(11, TEXTURE_2D, copyTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL43.glCopyImageSubData(reflectionBuffer.getRenderedTexture(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 1);
        renderer.getOpenGLContext().bindTexture(11, TEXTURE_2D, copyTextureId);

        if(!USE_COMPUTESHADER_FOR_REFLECTIONS) {
            reflectionBuffer.use(true);
            reflectionProgram.use();
            reflectionProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT);
            reflectionProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
            reflectionProgram.setUniform("useSSR", Config.useSSR);
            reflectionProgram.setUniform("screenWidth", (float) Config.WIDTH);
            reflectionProgram.setUniform("screenHeight", (float) Config.HEIGHT);
            reflectionProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
            reflectionProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
            renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(reflectionProgram);
            reflectionProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
            reflectionProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());
            renderer.getFullscreenBuffer().draw();
            reflectionBuffer.unuse();
        } else {
            GL42.glBindImageTexture(6, reflectionBuffer.getRenderedTexture(0), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F);
            tiledProbeLightingProgram.use();
            tiledProbeLightingProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT);
            tiledProbeLightingProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
            tiledProbeLightingProgram.setUniform("useSSR", Config.useSSR);
            tiledProbeLightingProgram.setUniform("screenWidth", (float) Config.WIDTH);
            tiledProbeLightingProgram.setUniform("screenHeight", (float) Config.HEIGHT);
            tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
            tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
            tiledProbeLightingProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
            renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(tiledProbeLightingProgram);
            tiledProbeLightingProgram.dispatchCompute(reflectionBuffer.getWidth()/16, reflectionBuffer.getHeight()/16, 1); //16+1
            //		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        }

        GL11.glDeleteTextures(copyTextureId);
        GPUProfiler.end();
    }


    public void combinePass(RenderTarget target, DirectionalLight light, Camera camera) {
        GBuffer gBuffer = renderer.getGBuffer();
        RenderTarget finalBuffer = gBuffer.getFinalBuffer();
        RenderTarget reflectionBuffer = gBuffer.getReflectionBuffer();
        renderer.getTextureFactory().generateMipMaps(finalBuffer.getRenderedTexture(0));

        combineProgram.use();
        combineProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        combineProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        combineProgram.setUniform("screenWidth", (float) Config.WIDTH);
        combineProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        combineProgram.setUniform("camPosition", camera.getPosition());
        combineProgram.setUniform("ambientColor", Config.AMBIENT_LIGHT);
        combineProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
        combineProgram.setUniform("worldExposure", Config.EXPOSURE);
        combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.AUTO_EXPOSURE_ENABLED);
        combineProgram.setUniform("fullScreenMipmapCount", gBuffer.getFullScreenMipmapCount());
        combineProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
        combineProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());

        finalBuffer.use(true);
        renderer.getOpenGLContext().disable(DEPTH_TEST);

        renderer.getOpenGLContext().bindTexture(0, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        renderer.getOpenGLContext().bindTexture(1, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        renderer.getOpenGLContext().bindTexture(2, TEXTURE_2D, gBuffer.getLightAccumulationBuffer().getRenderedTexture(1));
        renderer.getOpenGLContext().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        renderer.getOpenGLContext().bindTexture(4, TEXTURE_2D, gBuffer.getPositionMap());
        renderer.getOpenGLContext().bindTexture(5, TEXTURE_2D, gBuffer.getNormalMap());
        renderer.getEnvironmentMap().bind(6);
        renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray().bind(7);
        renderer.getOpenGLContext().bindTexture(8, TEXTURE_2D, gBuffer.getReflectionMap());
        renderer.getOpenGLContext().bindTexture(9, TEXTURE_2D, gBuffer.getRefractedMap());
        renderer.getOpenGLContext().bindTexture(11, TEXTURE_2D, gBuffer.getAmbientOcclusionScatteringMap());

        renderer.getFullscreenBuffer().draw();

        if(target == null) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        } else {
            target.use(true);
        }

        GPUProfiler.start("Post processing");
        postProcessProgram.use();
        renderer.getOpenGLContext().bindTexture(0, TEXTURE_2D, finalBuffer.getRenderedTexture(0));
        postProcessProgram.setUniform("worldExposure", Config.EXPOSURE);
        postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.AUTO_EXPOSURE_ENABLED);
        postProcessProgram.setUniform("usePostProcessing", Config.ENABLE_POSTPROCESSING);
        postProcessProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());
//        postProcessProgram.bindShaderStorageBuffer(1, AppContext.getInstance().getRenderer().getMaterialFactory().getMaterialBuffer());
        renderer.getOpenGLContext().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        renderer.getOpenGLContext().bindTexture(2, TEXTURE_2D, gBuffer.getMotionMap());
        renderer.getFullscreenBuffer().draw();

        GPUProfiler.end();
    }


    private void debugDrawProbes(Camera camera) {
        Entity probeBoxEntity = renderer.getGBuffer().getProbeBoxEntity();

        probeFirstpassProgram.use();
        renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(probeFirstpassProgram);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
        renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind();
        probeFirstpassProgram.setUniform("showContent", Config.DEBUGDRAW_PROBES_WITH_CONTENT);

        Vector3f oldMaterialColor = new Vector3f(probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse());

        for (EnvironmentProbe probe : renderer.getEnvironmentProbeFactory().getProbes()) {
            Transform transform = new Transform();
            transform.setPosition(probe.getCenter());
            transform.setScale(probe.getSize());
            Vector3f colorHelper = probe.getDebugColor();
            probeBoxEntity.getComponent(ModelComponent.class).getMaterial().setDiffuse(colorHelper);
            probeBoxEntity.setTransform(transform);
            probeBoxEntity.update(0);
            probeFirstpassProgram.setUniform("probeCenter", probe.getCenter());
            probeFirstpassProgram.setUniform("probeIndex", probe.getIndex());
            probeBoxEntity.getComponent(ModelComponent.class).
                    draw(camera, probeBoxEntity.getModelMatrixAsBuffer(), probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getFirstPassProgram(), -1);
        }

        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().x = oldMaterialColor.x;
        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().y = oldMaterialColor.y;
        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().z = oldMaterialColor.z;

    }

}
