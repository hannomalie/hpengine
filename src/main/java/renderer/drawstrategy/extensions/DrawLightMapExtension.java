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
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.constants.GlCap;
import renderer.drawstrategy.DrawStrategy;
import renderer.drawstrategy.FirstPassResult;
import renderer.drawstrategy.SecondPassResult;
import renderer.drawstrategy.SimpleDrawStrategy;
import renderer.material.MaterialFactory;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import scene.LightmapManager;
import shader.ComputeShaderProgram;
import shader.Program;
import shader.ProgramFactory;
import texture.TextureFactory;
import util.Util;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.util.*;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;
import static renderer.constants.GlTextureTarget.TEXTURE_2D;

public class DrawLightMapExtension implements RenderExtension {

    static FloatBuffer zeroBuffer = BufferUtils.createFloatBuffer(4);
    static {
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.rewind();
    }

    public static final int LIGHTMAP_INTERNAL_FORMAT = GL30.GL_RGBA16F;
    private static boolean DRAW_LIGHTMAP = true;
    private static int lightmapId = -1;
    private final Program lightMapProgram;
    private final ComputeShaderProgram lightmapPropagationProgram;
    private final ComputeShaderProgram lightmapDilationProgram;
    private final ComputeShaderProgram lightmapBoundingSphereProgram;
    private Program lightmapEvaluationProgram;
    private final FloatBuffer identityMatrix44Buffer;

    private List<CommandBuffer.DrawElementsIndirectCommand> commands = new ArrayList();
    private Map<Integer, CommandBuffer.DrawElementsIndirectCommand> commandsMap = new HashMap();
    private SortedSet<Integer> keys = new TreeSet<>();
    CommandBuffer globalCommandBuffer = new CommandBuffer(16000);

    private final int WIDTH = 128;
    private final int HEIGHT = 128;
    private final RenderTarget lightMapTarget = new RenderTargetBuilder()
            .setWidth(WIDTH)
            .setHeight(HEIGHT)
            .removeDepthAttachment()
            .add(6, new ColorAttachmentDefinition().setInternalFormat(LIGHTMAP_INTERNAL_FORMAT).setTextureFilter(GL11.GL_NEAREST))
            .build();
    private int currentCounter = 0;
    private int count = 10;
    private int currentTarget = 4;
    private int currentSource = 5;

    public DrawLightMapExtension() throws Exception {
        OpenGLContext.getInstance().execute(() -> {
            TextureFactory.getInstance().generateMipMaps(lightMapTarget.getRenderedTexture());
            TextureFactory.getInstance().generateMipMaps(lightMapTarget.getRenderedTexture(3));
            TextureFactory.getInstance().generateMipMaps(lightMapTarget.getRenderedTexture(4));
        });
        identityMatrix44Buffer = new Transform().getTransformationBuffer();
        lightMapProgram = ProgramFactory.getInstance().getProgram("lightmap_vertex.glsl", "lightmap_fragment.glsl");
        lightmapEvaluationProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "lightmap_evaluation_fragment.glsl");
        lightmapPropagationProgram = ProgramFactory.getInstance().getComputeProgram("lightmap_propagation_compute.glsl");
        lightmapDilationProgram = ProgramFactory.getInstance().getComputeProgram("lightmap_dilation_compute.glsl");
        lightmapBoundingSphereProgram = ProgramFactory.getInstance().getComputeProgram("lightmap_bounding_sphere_compute.glsl");

        Program cubeMapProgram = ProgramFactory.getInstance().getProgram("lightmap_cubemap_vertex.glsl", "lightmap_cubemap_geometry.glsl", "lightmap_cubemap_fragment.glsl", true);

        //TODO: Remove this crap
        lightmapId = lightMapTarget.getRenderedTexture();
    }

    @Override
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        if(DRAW_LIGHTMAP && (renderExtract.directionalLightNeedsShadowMapRender || currentCounter < count)) {
            if(currentCounter >= count) {
                currentSource = currentTarget;
                currentTarget = currentTarget == 4 ? 5 : 4;
                currentCounter = 0;
            }
            if(currentCounter == 0) {
                drawLightmap(renderExtract, firstPassResult);
            }


            GPUProfiler.start("Lightmap propagation");
            OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, lightMapTarget.getRenderedTexture());
            OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, lightMapTarget.getRenderedTexture(1));
            OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, lightMapTarget.getRenderedTexture(2));
            OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, lightMapTarget.getRenderedTexture(3));
            OpenGLContext.getInstance().bindImageTexture(4, lightMapTarget.getRenderedTexture(currentTarget), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
            OpenGLContext.getInstance().bindImageTexture(5, lightMapTarget.getRenderedTexture(currentSource), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);

            lightmapPropagationProgram.use();
            lightmapPropagationProgram.setUniform("count", count);
            lightmapPropagationProgram.setUniform("currentCounter", currentCounter);
            lightmapPropagationProgram.setUniform("start", (currentCounter * (WIDTH/count)));
            lightmapPropagationProgram.setUniform("amount", (WIDTH/count));
            lightmapPropagationProgram.setUniform("width", WIDTH);
            lightmapPropagationProgram.setUniform("height", HEIGHT);
            lightmapPropagationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);

//            boolean useThreeBounces = false;
//            if(useThreeBounces) {
//                OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, lightMapTarget.getRenderedTexture(4));
//                OpenGLContext.getInstance().bindImageTexture(4, lightMapTarget.getRenderedTexture(3), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
//                lightmapPropagationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);
//                OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, lightMapTarget.getRenderedTexture(3));
//                OpenGLContext.getInstance().bindImageTexture(4, lightMapTarget.getRenderedTexture(4), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
//                lightmapPropagationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);
//            }
//            GPUProfiler.end();

            GPUProfiler.start("Lightmap dilation");
            int dilationTimes = 0;
            for(int i = 0; i < dilationTimes; i++) {
                OpenGLContext.getInstance().bindImageTexture(1, lightMapTarget.getRenderedTexture(currentTarget), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
                lightmapDilationProgram.use();
                lightmapDilationProgram.setUniform("width", WIDTH);
                lightmapDilationProgram.setUniform("height", HEIGHT);
                lightmapDilationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);
            }
            GPUProfiler.end();

//            TextureFactory.getInstance().blur2DTextureRGBA16F(lightMapTarget.getRenderedTexture(currentTarget), lightMapTarget.getWidth(), lightMapTarget.getHeight(), 0, 0);

            currentCounter++;
        }
    }

    public void drawLightmap(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        lightMapTarget.use(false);
        ARBClearTexture.glClearTexImage(lightMapTarget.getRenderedTexture(currentTarget), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zeroBuffer);

        OpenGLContext openGLContext = OpenGLContext.getInstance();
        openGLContext.disable(CULL_FACE);
        openGLContext.depthMask(false);
        openGLContext.disable(DEPTH_TEST);

        OpenGLContext.getInstance().bindImageTexture(5, lightMapTarget.getRenderedTexture(currentSource), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
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
        lightMapProgram.setUniform("lightmapWidth", LightmapManager.getInstance().getWidth());
        lightMapProgram.setUniform("lightmapHeight", LightmapManager.getInstance().getHeight());
        lightMapProgram.setUniform("width", lightMapTarget.getWidth());
        lightMapProgram.setUniform("height", lightMapTarget.getHeight());

        GPUProfiler.start("Actual draw entities");
        ModelComponent.getGlobalIndexBuffer().bind();
        commandsMap.clear();
        for (PerEntityInfo info : renderExtract.perEntityInfos()) {
            OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
            int currentVerticesCount = info.getIndexCount() / 3;
            if (!SimpleDrawStrategy.INDIRECT_DRAWING) {
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
        if (SimpleDrawStrategy.INDIRECT_DRAWING) {
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
            globalCommandBuffer.put(Util.toArray(commands, CommandBuffer.DrawElementsIndirectCommand.class));
            globalCommandBuffer.bind();
            VertexBuffer.drawInstancedIndirectBaseVertex(ModelComponent.getGlobalVertexBuffer(), ModelComponent.getGlobalIndexBuffer(), globalCommandBuffer.getBuffer(), commands.size());
            ModelComponent.getGlobalIndexBuffer().unbind();
            globalCommandBuffer.unbind();
        }
        openGLContext.enable(CULL_FACE);
        openGLContext.depthMask(true);
        openGLContext.enable(DEPTH_TEST);

        lightmapBoundingSphereProgram.use();
        int width = WIDTH;
        int height = HEIGHT;
        for (int i = 1; i < Util.calculateMipMapCount(WIDTH, HEIGHT); i++) {
            width /= 2;
            height /= 2;
            OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, lightMapTarget.getRenderedTexture(0));
            OpenGLContext.getInstance().bindImageTexture(1, lightMapTarget.getRenderedTexture(0), i, false, 0, GL15.GL_WRITE_ONLY, LIGHTMAP_INTERNAL_FORMAT);
            lightmapBoundingSphereProgram.setUniform("width", width);
            lightmapBoundingSphereProgram.setUniform("height", height);
            lightmapBoundingSphereProgram.setUniform("mipmapSource", i - 1);
            lightmapBoundingSphereProgram.setUniform("mipmapTarget", i);
            lightmapBoundingSphereProgram.dispatchCompute(width / 8, height / 8, 1);
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
//        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);


        OpenGLContext.getInstance().bindTexture(9, TEXTURE_2D, getFinalLightmapTexture());
        TextureFactory.getInstance().getCubeMap().bind(10);
        OpenGLContext.getInstance().bindTexture(12, TEXTURE_2D, Renderer.getInstance().getGBuffer().getLightmapUVMap());

        lightmapEvaluationProgram.use();
        lightmapEvaluationProgram.setUniform("eyePosition", renderExtract.camera.getWorldPosition());
        lightmapEvaluationProgram.setUniformAsMatrix4("viewMatrix", renderExtract.camera.getViewMatrixAsBuffer());
        lightmapEvaluationProgram.setUniformAsMatrix4("projectionMatrix", renderExtract.camera.getProjectionMatrixAsBuffer());
        lightmapEvaluationProgram.bindShaderStorageBuffer(0, Renderer.getInstance().getGBuffer().getStorageBuffer());

        lightmapEvaluationProgram.setUniform("handle", TextureFactory.getInstance().getCubeMap().getHandle());
        lightmapEvaluationProgram.setUniform("screenWidth", (float) Config.WIDTH);
        lightmapEvaluationProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        QuadVertexBuffer.getFullscreenBuffer().draw();
        GPUProfiler.end();
    }

    public int getFinalLightmapTexture() {
        if(currentTarget == 4) {
            return getLightMapTarget().getRenderedTexture(5);
        }
        return getLightMapTarget().getRenderedTexture(4);
    }


    public RenderTarget getLightMapTarget() {
        return lightMapTarget;
    }

    public static int getRenderedTexture() {
        return lightmapId;
    }
}
