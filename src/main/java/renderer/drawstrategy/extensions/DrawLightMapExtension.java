package renderer.drawstrategy.extensions;

import component.ModelComponent;
import config.Config;
import engine.PerEntityInfo;
import engine.Transform;
import engine.model.CommandBuffer;
import engine.model.EntityFactory;
import engine.model.QuadVertexBuffer;
import engine.model.VertexBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.constants.GlCap;
import renderer.drawstrategy.DrawStrategy;
import renderer.drawstrategy.FirstPassResult;
import renderer.drawstrategy.SecondPassResult;
import renderer.drawstrategy.SimpleDrawStrategy;
import renderer.environmentsampler.LightmapEnvironmentSampler;
import renderer.material.MaterialFactory;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import scene.EnvironmentProbeFactory;
import shader.Program;
import shader.ProgramFactory;
import texture.TextureFactory;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.*;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;
import static renderer.constants.GlTextureTarget.*;

public class DrawLightMapExtension implements RenderExtension {

    public static final int PROBE_RESOLUTION = 32;
    public static final int PROBE_SIZE = 4;
    public static final int PROBE_COUNT_X = 10;
    public static final int PROBE_COUNT_X_HALF = PROBE_COUNT_X/2;
    public static final int PROBE_COUNT_Z = 10;
    public static final int PROBE_COUNT_Z_HALF = PROBE_COUNT_Z/2;
    public static final int PROBE_COUNT = PROBE_COUNT_X * PROBE_COUNT_Z;
    private static boolean DRAW_LIGHTMAP = true;
    private final Program lightMapProgram;
    private final LongBuffer cubemapHandles;
    private final long[] cubemapHandlesAsLongs;
    private Program lightmapEvaluationProgram;
    private final FloatBuffer identityMatrix44Buffer;

    private List<CommandBuffer.DrawElementsIndirectCommand> commands = new ArrayList();
    private Map<Integer, CommandBuffer.DrawElementsIndirectCommand> commandsMap = new HashMap();
    private SortedSet<Integer> keys = new TreeSet<>();
    CommandBuffer globalCommandBuffer = new CommandBuffer(16000);

    private List<LightmapEnvironmentSampler> samplers = new ArrayList<>();

    private final RenderTarget lightMapTarget = new RenderTargetBuilder()
            .setWidth(1024)
            .setHeight(1024)
            .removeDepthAttachment()
            .add(new ColorAttachmentDefinition().setInternalFormat(GL11.GL_RGBA8))
            .build();

    public DrawLightMapExtension() throws Exception {
        identityMatrix44Buffer = new Transform().getTransformationBuffer();
        lightMapProgram = ProgramFactory.getInstance().getProgram("lightmap_vertex.glsl", "lightmap_fragment.glsl");
        lightmapEvaluationProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "lightmap_evaluation_fragment.glsl");

        for(int x = -PROBE_COUNT_X_HALF; x < PROBE_COUNT_X_HALF; x+= 1) {
            for(int z = -PROBE_COUNT_Z_HALF; z < PROBE_COUNT_Z_HALF; z+= 1) {
                LightmapEnvironmentSampler currentSampler = new LightmapEnvironmentSampler(new Vector3f(x * PROBE_SIZE, PROBE_SIZE/2, z * PROBE_SIZE));
                samplers.add(currentSampler);
                System.out.println("currentSampler.getPosition() = " + currentSampler.getPosition());
            }
        }
        cubemapHandlesAsLongs = new long[PROBE_COUNT];
        for(int i = 0; i < PROBE_COUNT; i++) {
            cubemapHandlesAsLongs[i] = samplers.get(i).getCubeMapViewHandle();
        }
        cubemapHandles = BufferUtils.createLongBuffer(cubemapHandlesAsLongs.length);
        cubemapHandles.put(cubemapHandlesAsLongs);
        cubemapHandles.rewind();
    }

    @Override
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        if(DRAW_LIGHTMAP && renderExtract.directionalLightNeedsShadowMapRender) {
            lightMapTarget.use(true);
            OpenGLContext openGLContext = OpenGLContext.getInstance();
            openGLContext.disable(CULL_FACE);
            openGLContext.depthMask(false);
            openGLContext.disable(DEPTH_TEST);

            lightMapProgram.use();

            lightMapProgram.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
            lightMapProgram.bindShaderStorageBuffer(3, EntityFactory.getInstance().getEntitiesBuffer());
            lightMapProgram.bindShaderStorageBuffer(4, ModelComponent.getGlobalEntityOffsetBuffer());

            lightMapProgram.setUniformAsMatrix4("shadowMatrix", renderExtract.directionalLight.getViewProjectionMatrixAsBuffer());
            lightMapProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            lightMapProgram.setUniformAsMatrix4("viewMatrix", renderExtract.camera.getViewMatrixAsBuffer());
            lightMapProgram.setUniformAsMatrix4("projectionMatrix", renderExtract.camera.getProjectionMatrixAsBuffer());
            lightMapProgram.setUniform("lightDirection", renderExtract.directionalLight.getDirection());
            lightMapProgram.setUniform("lightDiffuse", renderExtract.directionalLight.getColor());


            GPUProfiler.start("Actual draw entities");
            ModelComponent.getGlobalIndexBuffer().bind();
            commandsMap.clear();
            for(PerEntityInfo info : renderExtract.perEntityInfos()) {
                OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
                int currentVerticesCount = info.getIndexCount()/3;
                if(!SimpleDrawStrategy.INDIRECT_DRAWING) {
                    currentVerticesCount = DrawStrategy.draw(info);
                } else {
//                    info.getMaterial().setTexturesUsed();
                    int count = info.getIndexCount();
                    int firstIndex = info.getIndexOffset();
                    int primCount = info.getInstanceCount();
                    int baseVertex = info.getBaseVertex();
                    int baseInstance = 0;

                    CommandBuffer.DrawElementsIndirectCommand command = new CommandBuffer.DrawElementsIndirectCommand(count, primCount, firstIndex, baseVertex, baseInstance, info.getEntityBaseIndex());
                    commandsMap.put(info.getEntityIndex(), command);
                }

                firstPassResult.verticesDrawn += currentVerticesCount;
                if (currentVerticesCount > 0) {
                    firstPassResult.entitiesDrawn++;
                }
            }
            if(SimpleDrawStrategy.INDIRECT_DRAWING) {
                keys.clear();
                keys.addAll(commandsMap.keySet());
                commands.clear();
                for (Integer key : keys) {
                    commands.add(commandsMap.get(key));
                }
                lightMapProgram.setUniform("entityIndex", 0);
                lightMapProgram.setUniform("entityBaseIndex", 0);
                lightMapProgram.setUniform("entityCount", commands.size());
                ModelComponent.getGlobalEntityOffsetBuffer().put(0, commands.stream().mapToInt(c -> c.entityOffset).toArray());
                globalCommandBuffer.put(util.Util.toArray(commands, CommandBuffer.DrawElementsIndirectCommand.class));
                globalCommandBuffer.bind();
                VertexBuffer.drawInstancedIndirectBaseVertex(ModelComponent.getGlobalVertexBuffer(),ModelComponent.getGlobalIndexBuffer(), globalCommandBuffer.getBuffer(), commands.size());
                ModelComponent.getGlobalIndexBuffer().unbind();
                globalCommandBuffer.unbind();
            }
            openGLContext.enable(CULL_FACE);
            openGLContext.depthMask(true);
            openGLContext.enable(DEPTH_TEST);

//            TextureFactory.getInstance().blur2DTextureRGBA16F(lightMapTarget.getRenderedTexture(), lightMapTarget.getWidth(), lightMapTarget.getHeight(), 0, 0);

            GPUProfiler.start("Draw lightmap probes");
            for(LightmapEnvironmentSampler sampler : samplers) {
                sampler.drawCubeMap(false, null);
            }
            GPUProfiler.end();
        }
    }

    @Override
    public void renderSecondPassFullScreen(RenderExtract renderExtract, SecondPassResult secondPassResult) {

        GPUProfiler.start("Evaluate lightmap");
        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        OpenGLContext.getInstance().bindTexture(7, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        EnvironmentProbeFactory.getInstance().getLightmapEnvironmentMapsArray().bind(8);
//        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);

        OpenGLContext.getInstance().bindTexture(9, TEXTURE_2D, getLightMapTarget().getRenderedTexture());
//        TextureFactory.getInstance().getCubeMap().bind(10);
        OpenGLContext.getInstance().bindTexture(10, TEXTURE_CUBE_MAP, samplers.get(0).getCubeMapView());

        lightmapEvaluationProgram.use();
        lightmapEvaluationProgram.setUniform("probeSize", PROBE_SIZE);
        lightmapEvaluationProgram.setUniform("eyePosition", renderExtract.camera.getWorldPosition());
        lightmapEvaluationProgram.setUniformAsMatrix4("viewMatrix", renderExtract.camera.getViewMatrixAsBuffer());
        lightmapEvaluationProgram.setUniformAsMatrix4("projectionMatrix", renderExtract.camera.getProjectionMatrixAsBuffer());
        lightmapEvaluationProgram.bindShaderStorageBuffer(0, Renderer.getInstance().getGBuffer().getStorageBuffer());

//        lightmapEvaluationProgram.setUniform("handle", EnvironmentProbeFactory.getInstance().getLightMapCubeMapArrayRenderTarget().getHandleLists().get(0)[0]);
        lightmapEvaluationProgram.setUniform("handles", cubemapHandles);
        lightmapEvaluationProgram.setUniform("handle", TextureFactory.getInstance().getCubeMap().getHandle());
        lightmapEvaluationProgram.setUniform("screenWidth", (float) Config.WIDTH);
        lightmapEvaluationProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        QuadVertexBuffer.getFullscreenBuffer().draw();
        GPUProfiler.end();
    }


    public RenderTarget getLightMapTarget() {
        return lightMapTarget;
    }

    public List<LightmapEnvironmentSampler> getSamplers() {
        return samplers;
    }
}
