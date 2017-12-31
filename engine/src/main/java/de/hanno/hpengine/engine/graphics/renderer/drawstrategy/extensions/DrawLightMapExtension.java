package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.model.NewLightmapManager;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.renderer.Pipeline;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.model.texture.TextureFactory;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.nio.FloatBuffer;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;

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

    Pipeline pipeline;

    public static int WIDTH = 256;
    public static int HEIGHT = WIDTH;
    public static RenderTarget staticLightmapTarget;
    public final RenderTarget lightMapTarget = new RenderTargetBuilder()
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
        staticLightmapTarget = lightMapTarget;
        GraphicsContext.getInstance().execute(() -> {
            TextureFactory.getInstance().generateMipMaps(lightMapTarget.getRenderedTexture());
            TextureFactory.getInstance().generateMipMaps(lightMapTarget.getRenderedTexture(3));
            TextureFactory.getInstance().generateMipMaps(lightMapTarget.getRenderedTexture(4));
        });
        identityMatrix44Buffer = new SimpleTransform().getTransformationBuffer();
        lightMapProgram = ProgramFactory.getInstance().getProgram("lightmap_vertex.glsl", "lightmap_fragment.glsl");
        lightmapEvaluationProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "lightmap_evaluation_fragment.glsl");
        lightmapPropagationProgram = ProgramFactory.getInstance().getComputeProgram("lightmap_propagation_compute.glsl");
        lightmapDilationProgram = ProgramFactory.getInstance().getComputeProgram("lightmap_dilation_compute.glsl");
        lightmapBoundingSphereProgram = ProgramFactory.getInstance().getComputeProgram("lightmap_bounding_sphere_compute.glsl");

        Program cubeMapProgram = ProgramFactory.getInstance().getProgram(true, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "lightmap_cubemap_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "lightmap_cubemap_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "lightmap_cubemap_fragment.glsl")));

        //TODO: Remove this crap
        lightmapId = lightMapTarget.getRenderedTexture();

        pipeline = new Pipeline(false, false, false);
    }

    private long firstPassRenderedInCycle;
    @Override
    public void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {
        if(DRAW_LIGHTMAP && (renderState.directionalLightHasMovedInCycle > firstPassRenderedInCycle || currentCounter < count)) {
            if(currentCounter >= count) {
                currentSource = currentTarget;
                currentTarget = currentTarget == 4 ? 5 : 4;
                currentCounter = 0;
            }
            if(currentCounter == 0) {
                drawLightmap(renderState, firstPassResult);
            }


            GPUProfiler.start("Lightmap propagation");
            GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, lightMapTarget.getRenderedTexture());
            GraphicsContext.getInstance().bindTexture(1, TEXTURE_2D, lightMapTarget.getRenderedTexture(1));
            GraphicsContext.getInstance().bindTexture(2, TEXTURE_2D, lightMapTarget.getRenderedTexture(2));
            GraphicsContext.getInstance().bindTexture(3, TEXTURE_2D, lightMapTarget.getRenderedTexture(3));
            GraphicsContext.getInstance().bindImageTexture(4, lightMapTarget.getRenderedTexture(currentTarget), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
            GraphicsContext.getInstance().bindImageTexture(5, lightMapTarget.getRenderedTexture(currentSource), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);

            lightmapPropagationProgram.use();
            lightmapPropagationProgram.setUniform("count", count);
            lightmapPropagationProgram.setUniform("currentCounter", currentCounter);
            lightmapPropagationProgram.setUniform("start", (currentCounter * (WIDTH/count)));
            lightmapPropagationProgram.setUniform("amount", (WIDTH/count));
            lightmapPropagationProgram.setUniform("width", WIDTH);
            lightmapPropagationProgram.setUniform("height", HEIGHT);
            lightmapPropagationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);

//            boolean useThreeBounces = true;
//            if(useThreeBounces) {
//                OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, lightMapTarget.getRenderedTexture(4));
//                OpenGLContext.getInstance().bindImageTexture(4, lightMapTarget.getRenderedTexture(3), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
//                lightmapPropagationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);
//                OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, lightMapTarget.getRenderedTexture(3));
//                OpenGLContext.getInstance().bindImageTexture(4, lightMapTarget.getRenderedTexture(4), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
//                lightmapPropagationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);
//            }
            GPUProfiler.end();

            GPUProfiler.start("Lightmap dilation");
            int dilationTimes = 0;
            for(int i = 0; i < dilationTimes; i++) {
                GraphicsContext.getInstance().bindImageTexture(1, lightMapTarget.getRenderedTexture(currentTarget), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
                lightmapDilationProgram.use();
                lightmapDilationProgram.setUniform("width", WIDTH);
                lightmapDilationProgram.setUniform("height", HEIGHT);
                lightmapDilationProgram.dispatchCompute(WIDTH/16,HEIGHT/16,1);
            }
            GPUProfiler.end();

//            TextureFactory.getInstance().blur2DTextureRGBA16F(lightMapTarget.getRenderedTexture(currentTarget), lightMapTarget.getWidth(), lightMapTarget.getHeight(), 0, 0);

            currentCounter++;
            firstPassRenderedInCycle = renderState.getCycle();
        }
    }

    public void drawLightmap(RenderState renderState, FirstPassResult firstPassResult) {
        lightMapTarget.use(false);
        ARBClearTexture.glClearTexImage(lightMapTarget.getRenderedTexture(currentTarget), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
        ARBClearTexture.glClearTexImage(lightMapTarget.getRenderedTexture(0), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
        ARBClearTexture.glClearTexImage(lightMapTarget.getRenderedTexture(1), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
        ARBClearTexture.glClearTexImage(lightMapTarget.getRenderedTexture(2), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
        ARBClearTexture.glClearTexImage(lightMapTarget.getRenderedTexture(3), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zeroBuffer);

        GraphicsContext graphicsContext = GraphicsContext.getInstance();
        graphicsContext.disable(CULL_FACE);
        graphicsContext.depthMask(false);
        graphicsContext.disable(DEPTH_TEST);

        graphicsContext.bindImageTexture(5, lightMapTarget.getRenderedTexture(currentSource), 0, false, 0, GL15.GL_READ_WRITE, LIGHTMAP_INTERNAL_FORMAT);
        lightMapProgram.use();

        lightMapProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
        lightMapProgram.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
//        lightMapProgram.bindShaderStorageBuffer(4, pipeline.getEntityOffsetBufferStatic()); //TODO: Check if this is needed

        lightMapProgram.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
        lightMapProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
        lightMapProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        lightMapProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        lightMapProgram.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection);
        lightMapProgram.setUniform("lightDiffuse", renderState.directionalLightState.directionalLightColor);
        lightMapProgram.setUniform("lightmapWidth", (float) NewLightmapManager.MAX_WIDTH);
        lightMapProgram.setUniform("lightmapHeight", (float) NewLightmapManager.MAX_HEIGHT);
        lightMapProgram.setUniform("width", lightMapTarget.getWidth());
        lightMapProgram.setUniform("height", lightMapTarget.getHeight());

        GPUProfiler.start("Actual draw entities lightmap");
        if (Config.getInstance().isIndirectRendering()) {
            if(true) {
                pipeline.prepareAndDraw(renderState, lightMapProgram, lightMapProgram, firstPassResult);
            } else {
                pipeline.draw(renderState, lightMapProgram, lightMapProgram, firstPassResult);
            }
        } else {
            for (RenderBatch info : renderState.getRenderBatchesStatic()) {
                GraphicsContext.getInstance().disable(GlCap.CULL_FACE);
                int currentVerticesCount = DrawStrategy.draw(renderState, info);
                firstPassResult.verticesDrawn += currentVerticesCount;
                if (currentVerticesCount > 0) {
                    firstPassResult.entitiesDrawn++;
                }
            }
        }
        GPUProfiler.end();
        graphicsContext.enable(CULL_FACE);
        graphicsContext.depthMask(true);
        graphicsContext.enable(DEPTH_TEST);

        lightmapBoundingSphereProgram.use();
        int width = WIDTH;
        int height = HEIGHT;
        for (int i = 1; i < Util.calculateMipMapCount(WIDTH, HEIGHT); i++) {
            width /= 2;
            height /= 2;
            GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, lightMapTarget.getRenderedTexture(0));
            GraphicsContext.getInstance().bindImageTexture(1, lightMapTarget.getRenderedTexture(0), i, false, 0, GL15.GL_WRITE_ONLY, LIGHTMAP_INTERNAL_FORMAT);
            lightmapBoundingSphereProgram.setUniform("width", width);
            lightmapBoundingSphereProgram.setUniform("height", height);
            lightmapBoundingSphereProgram.setUniform("mipmapSource", i - 1);
            lightmapBoundingSphereProgram.setUniform("mipmapTarget", i);
            lightmapBoundingSphereProgram.dispatchCompute(width / 8, height / 8, 1);
        }
    }

    @Override
    public void renderSecondPassFullScreen(RenderState renderState, SecondPassResult secondPassResult) {
        GPUProfiler.start("Evaluate lightmap");
        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        GraphicsContext.getInstance().bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        GraphicsContext.getInstance().bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        GraphicsContext.getInstance().bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        GraphicsContext.getInstance().bindTexture(7, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        GraphicsContext.getInstance().bindTexture(9, TEXTURE_2D, getFinalLightmapTexture());
        TextureFactory.getInstance().getCubeMap().bind(10);
        GraphicsContext.getInstance().bindTexture(12, TEXTURE_2D, Renderer.getInstance().getGBuffer().getLightmapUVMap());

        lightmapEvaluationProgram.use();
        lightmapEvaluationProgram.setUniform("eyePosition", renderState.camera.getPosition());
        lightmapEvaluationProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        lightmapEvaluationProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        lightmapEvaluationProgram.bindShaderStorageBuffer(0, Renderer.getInstance().getGBuffer().getStorageBuffer());

        lightmapEvaluationProgram.setUniform("handle", TextureFactory.getInstance().getCubeMap().getHandle());
        lightmapEvaluationProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        lightmapEvaluationProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
        QuadVertexBuffer.getFullscreenBuffer().draw();
        GPUProfiler.end();
    }

    public int getFinalLightmapTexture() {
        if(currentTarget == 4) {
            return getLightMapTarget().getRenderedTexture(5);
        }
        return getLightMapTarget().getRenderedTexture(4);
    }

    public int getLightMapPosition() {
        return getLightMapTarget().getRenderedTexture();
    }
    public int getLightMapNormal() {
        return getLightMapTarget().getRenderedTexture(1);
    }
    public int getLightMapAlbedo() {
        return getLightMapTarget().getRenderedTexture(2);
    }
    public int getLightMapColor() {
        return getLightMapTarget().getRenderedTexture(3);
    }


    public RenderTarget getLightMapTarget() {
        return lightMapTarget;
    }

    public static int getRenderedTexture() {
        return lightmapId;
    }
}
