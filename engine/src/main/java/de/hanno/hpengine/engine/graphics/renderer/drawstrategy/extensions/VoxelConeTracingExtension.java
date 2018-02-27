package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import com.carrotsearch.hppc.ObjectLongHashMap;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.*;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.SimplePipeline;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.model.Update;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.joml.AxisAngle4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.File;
import java.nio.FloatBuffer;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.*;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_3D;

public class VoxelConeTracingExtension implements RenderExtension {
    public static final int gridSize = 256;
    public static final int gridSizeHalf = gridSize/2;
    public static final int gridTextureFormat = GL11.GL_RGBA;//GL11.GL_R;
    public static final int gridTextureFormatSized = GL11.GL_RGBA8;//GL30.GL_R32UI;
    private final Engine engine;
    private Transform viewXTransform;
    private Transform viewYTransform;
    private Transform viewZTransform;

    public float sceneScale = 2;

    private Matrix4f ortho = de.hanno.hpengine.util.Util.createOrthogonal(-getGridSizeHalfScaled(), getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled());
    private Camera orthoCam;
    private Matrix4f viewX;
    public FloatBuffer viewXBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f viewY;
    public FloatBuffer viewYBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f viewZ;
    public FloatBuffer viewZBuffer = BufferUtils.createFloatBuffer(16);

    public static FloatBuffer ZERO_BUFFER = BufferUtils.createFloatBuffer(4);
    static {
        ZERO_BUFFER.put(0);
        ZERO_BUFFER.put(0);
        ZERO_BUFFER.put(0);
        ZERO_BUFFER.put(0);
        ZERO_BUFFER.rewind();
    }

    private final Program voxelizer;
    private final Program voxelConeTraceProgram;
    private final ComputeShaderProgram texture3DMipMapAlphaBlendComputeProgram;
    private final ComputeShaderProgram texture3DMipMapComputeProgram;
    private final ComputeShaderProgram clearDynamicVoxelsComputeProgram;
    private final ComputeShaderProgram injectLightComputeProgram;
    private final ComputeShaderProgram injectMultipleBounceLightComputeProgram;

    private final int albedoGrid;
    int normalGrid;
    int currentVoxelTarget;
    int currentVoxelSource;

    public final int grid;
    private final int gridTwo;
    private int lightInjectedCounter;

    private SimplePipeline pipeline;
    private FirstPassResult firstPassResult = new FirstPassResult();
    private boolean useIndirectDrawing = false;

    public VoxelConeTracingExtension(Engine engine) throws Exception {
        this.engine = engine;
        initOrthoCam();
        initViewXBuffer();
        initViewYBuffer();
        initViewZBuffer();

        voxelizer = this.engine.getProgramManager().getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "voxelize_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "voxelize_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "voxelize_fragment.glsl")), new Defines());
        texture3DMipMapAlphaBlendComputeProgram = this.engine.getProgramManager().getComputeProgram("texture3D_mipmap_alphablend_compute.glsl");
        texture3DMipMapComputeProgram = this.engine.getProgramManager().getComputeProgram("texture3D_mipmap_compute.glsl");
        clearDynamicVoxelsComputeProgram = this.engine.getProgramManager().getComputeProgram("texture3D_clear_dynamic_voxels_compute.glsl");
        injectLightComputeProgram = this.engine.getProgramManager().getComputeProgram("texture3D_inject_light_compute.glsl");
        injectMultipleBounceLightComputeProgram = this.engine.getProgramManager().getComputeProgram("texture3D_inject_bounce_light_compute.glsl");

        grid = this.engine.getTextureManager().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        gridTwo = this.engine.getTextureManager().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        albedoGrid = this.engine.getTextureManager().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        normalGrid = this.engine.getTextureManager().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_NEAREST,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);

        currentVoxelTarget = grid;
        currentVoxelSource = gridTwo;
        voxelConeTraceProgram = this.engine.getProgramManager().getProgramFromFileNames("passthrough_vertex.glsl", "voxel_cone_trace_fragment.glsl", new Defines());
        Config.getInstance().setUseAmbientOcclusion(false);
        pipeline = new SimplePipeline(engine, false, false, false);
        orthoCam = new Camera(engine.getSceneManager().getScene().getEntityManager().create(), ortho, getGridSizeHalfScaled(), -getGridSizeHalfScaled(), 90, 1);
    }

    private long entityMovedLastInCycle;
    private long directionalLightMovedLastInCycle;
    private long pointLightMovedLastInCycle;

    @Override
    public void renderFirstPass(Engine engine, GpuContext gpuContext, FirstPassResult firstPassResult, RenderState renderState) {
        GPUProfiler.start("VCT first pass");
        boolean entityMoved = renderState.getEntitiesState().entityMovedInCycle > entityMovedLastInCycle;
        boolean directionalLightMoved = renderState.getDirectionalLightHasMovedInCycle() > directionalLightMovedLastInCycle;
        boolean pointlightMoved = renderState.getPointlightMovedInCycle() > pointLightMovedLastInCycle;
        if(entityMoved) {
            entityMovedLastInCycle = renderState.getCycle();
        }
        if(pointlightMoved) {
            pointLightMovedLastInCycle = renderState.getCycle();
            lightInjectedCounter = 0;
        }
        if(directionalLightMoved) {
            directionalLightMovedLastInCycle = renderState.getCycle();
            lightInjectedCounter = 0;
        }
        boolean useVoxelConeTracing = true;
        boolean clearVoxels = true;
        int bounces = 1;

        boolean needsRevoxelization = useVoxelConeTracing && (!renderState.getSceneInitiallyDrawn() || Config.getInstance().isForceRevoxelization() || renderState.getRenderBatchesStatic().stream().anyMatch(info -> info.getUpdate().equals(Update.DYNAMIC)));
        if(entityMoved || needsRevoxelization) {
            lightInjectedCounter = 0;
        }
        boolean needsLightInjection = lightInjectedCounter < bounces || directionalLightMoved;

        voxelizeScene(renderState, clearVoxels, needsRevoxelization);
        injectLight(renderState, bounces, lightInjectedCounter, needsLightInjection);
        GPUProfiler.end();
    }

    public void injectLight(RenderState renderState, int bounces, int lightInjectedFramesAgo, boolean needsLightInjection) {
        if(needsLightInjection) {
            GPUProfiler.start("grid shading");
            GL42.glBindImageTexture(0, currentVoxelTarget, 0, false, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            engine.getGpuContext().bindTexture(1, TEXTURE_3D, albedoGrid);
            engine.getGpuContext().bindTexture(2, TEXTURE_3D, normalGrid);
            engine.getGpuContext().bindTexture(3, TEXTURE_3D, currentVoxelSource);
            int num_groups_xyz = Math.max(gridSize / 8, 1);

            if(lightInjectedFramesAgo == 0)
            {
                injectLightComputeProgram.use();

                injectLightComputeProgram.setUniform("pointLightCount", engine.getSceneManager().getScene().getPointLights().size());
                injectLightComputeProgram.bindShaderStorageBuffer(2, engine.getScene().getLightManager().getLightBuffer());
                injectLightComputeProgram.setUniform("bounces", bounces);
                injectLightComputeProgram.setUniform("sceneScale", getSceneScale(renderState));
                injectLightComputeProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
                injectLightComputeProgram.setUniform("gridSize", gridSize);
                injectLightComputeProgram.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
                injectLightComputeProgram.setUniform("lightDirection", renderState.getDirectionalLightState().directionalLightDirection);
                injectLightComputeProgram.setUniform("lightColor", renderState.getDirectionalLightState().directionalLightColor);

                injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
            }
            else
            {
                injectMultipleBounceLightComputeProgram.use();
                injectMultipleBounceLightComputeProgram.setUniform("bounces", bounces);
                injectMultipleBounceLightComputeProgram.setUniform("lightInjectedFramesAgo", lightInjectedFramesAgo);
                injectMultipleBounceLightComputeProgram.setUniform("sceneScale", getSceneScale(renderState));
                injectMultipleBounceLightComputeProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
                injectMultipleBounceLightComputeProgram.setUniform("gridSize", gridSize);
                injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
            }
            lightInjectedCounter++;
            mipmapGrid();
            switchCurrentVoxelGrid();
            GPUProfiler.end();
        }

        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        GL11.glColorMask(true, true, true, true);
    }

    public void voxelizeScene(RenderState renderState, boolean clearVoxels, boolean needsRevoxelization) {
        if(needsRevoxelization && clearVoxels) {
            GPUProfiler.start("Clear voxels");
            if(Config.getInstance().isForceRevoxelization() || !renderState.getSceneInitiallyDrawn()) {
                ARBClearTexture.glClearTexImage(currentVoxelTarget, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
                ARBClearTexture.glClearTexImage(normalGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
                ARBClearTexture.glClearTexImage(albedoGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
            } else {
                clearDynamicVoxelsComputeProgram.use();
                int num_groups_xyz = Math.max(gridSize / 8, 1);
                GL42.glBindImageTexture(0, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                GL42.glBindImageTexture(1, normalGrid, 0, true, 0, GL15.GL_READ_WRITE, gridTextureFormatSized);
                GL42.glBindImageTexture(3, currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                engine.getGpuContext().bindTexture(4, TEXTURE_3D, normalGrid);
                clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz,num_groups_xyz,num_groups_xyz);
            }
            GPUProfiler.end();
        }

        if(needsRevoxelization) {
            GPUProfiler.start("Voxelization");
            float sceneScale = getSceneScale(renderState);
            int gridSizeScaled = (int) (gridSize * sceneScale);
            engine.getGpuContext().viewPort(0, 0, gridSizeScaled, gridSizeScaled);
            voxelizer.use();
            GL42.glBindImageTexture(3, normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            GL42.glBindImageTexture(5, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            voxelizer.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
            voxelizer.setUniform("lightDirection", renderState.getDirectionalLightState().directionalLightDirection);
            voxelizer.setUniform("lightColor", renderState.getDirectionalLightState().directionalLightColor);
            voxelizer.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
            voxelizer.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
            voxelizer.setUniformAsMatrix4("u_MVPx", viewXBuffer);
            voxelizer.setUniformAsMatrix4("u_MVPy", viewYBuffer);
            voxelizer.setUniformAsMatrix4("u_MVPz", viewZBuffer);
            FloatBuffer viewMatrixAsBuffer1 = orthoCam.getEntity().getViewMatrixAsBuffer();
            voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
            FloatBuffer projectionMatrixAsBuffer1 = orthoCam.getProjectionMatrixAsBuffer();
            voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
            voxelizer.setUniform("u_width", gridSize);
            voxelizer.setUniform("u_height", gridSize);

            voxelizer.setUniform("writeVoxels", true);
            voxelizer.setUniform("sceneScale", sceneScale);
            voxelizer.setUniform("inverseSceneScale", 1f / sceneScale);
            voxelizer.setUniform("gridSize", gridSize);
            engine.getGpuContext().depthMask(false);
            engine.getGpuContext().disable(DEPTH_TEST);
            engine.getGpuContext().disable(BLEND);
            engine.getGpuContext().disable(CULL_FACE);
            GL11.glColorMask(false, false, false, false);


            if(useIndirectDrawing && Config.getInstance().isIndirectRendering()) {
                firstPassResult.reset();
                pipeline.prepareAndDraw(renderState, voxelizer, voxelizer, firstPassResult);
            } else {
                for (RenderBatch entity : renderState.getRenderBatchesStatic()) {
                    boolean isStatic = entity.getUpdate().equals(Update.STATIC);
                    if (renderState.getSceneInitiallyDrawn() && !Config.getInstance().isForceRevoxelization() && isStatic) {
                        continue;
                    }
                    int currentVerticesCount = DrawStrategy.draw(engine.getGpuContext(), renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), entity, voxelizer, false);

//                TODO: Count this somehow?
//                firstPassResult.verticesDrawn += currentVerticesCount;
//                if (currentVerticesCount > 0) {
//                    firstPassResult.entitiesDrawn++;
//                }
                }
            }
            GPUProfiler.end();
        }
    }
    private ObjectLongHashMap movedInCycleCash = new ObjectLongHashMap();

    private void mipmapGrid() {
        boolean generatevoxelsMipmap = true;
        if(generatevoxelsMipmap){
            GPUProfiler.start("grid mipmap");
            GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
            mipmapGrid(currentVoxelTarget, texture3DMipMapAlphaBlendComputeProgram);
//                mipmapGrid(normalGrid, texture3DMipMapComputeProgram);

            GPUProfiler.end();
        }
    }

    private void mipmapGrid(int texture3D, ComputeShaderProgram shader) {
        shader.use();
        int size = gridSize;
        int currentSizeSource = (2*size);//(int) (sceneScale*size);
        int currentMipMapLevel = 0;

        while(currentSizeSource > 1) {
            currentSizeSource /= 2;
            int currentSizeTarget = currentSizeSource / 2;
            currentMipMapLevel++;

            GL42.glBindImageTexture(0, texture3D, currentMipMapLevel-1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized);
            GL42.glBindImageTexture(1, texture3D, currentMipMapLevel, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            GL42.glBindImageTexture(3, normalGrid, currentMipMapLevel-1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized);
            shader.setUniform("sourceSize", currentSizeSource);
            shader.setUniform("targetSize", currentSizeTarget);

            int num_groups_xyz = Math.max(currentSizeTarget / 8, 1);
            shader.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
        }
    }

    private void switchCurrentVoxelGrid() {
        if(currentVoxelTarget == grid) {
            currentVoxelTarget = gridTwo;
            currentVoxelSource = grid;
        } else {
            currentVoxelTarget = grid;
            currentVoxelSource = gridTwo;
        }
    }

    @Override
    public void renderSecondPassFullScreen(RenderState renderState, SecondPassResult secondPassResult) {
        GPUProfiler.start("VCT second pass");
        engine.getGpuContext().bindTexture(0, TEXTURE_2D, engine.getRenderer().getGBuffer().getPositionMap());
        engine.getGpuContext().bindTexture(1, TEXTURE_2D, engine.getRenderer().getGBuffer().getNormalMap());
        engine.getGpuContext().bindTexture(2, TEXTURE_2D, engine.getRenderer().getGBuffer().getColorReflectivenessMap());
        engine.getGpuContext().bindTexture(3, TEXTURE_2D, engine.getRenderer().getGBuffer().getMotionMap());
        engine.getGpuContext().bindTexture(7, TEXTURE_2D, engine.getRenderer().getGBuffer().getVisibilityMap());
        engine.getGpuContext().bindTexture(12, TEXTURE_3D, albedoGrid);
        engine.getGpuContext().bindTexture(13, TEXTURE_3D, currentVoxelSource);
        engine.getGpuContext().bindTexture(14, TEXTURE_3D, normalGrid);

        voxelConeTraceProgram.use();
        Vector3f camTranslation = new Vector3f();
        voxelConeTraceProgram.setUniform("eyePosition", renderState.getCamera().getEntity().getTranslation(camTranslation));
        voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderState.getCamera().getEntity().getViewMatrixAsBuffer());
        voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderState.getCamera().getProjectionMatrixAsBuffer());
        voxelConeTraceProgram.bindShaderStorageBuffer(0, engine.getRenderer().getGBuffer().getStorageBuffer());
        voxelConeTraceProgram.setUniform("sceneScale", getSceneScale(renderState));
        voxelConeTraceProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
        voxelConeTraceProgram.setUniform("gridSize", gridSize);
        voxelConeTraceProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
        voxelConeTraceProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        voxelConeTraceProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
        engine.getGpuContext().getFullscreenBuffer().draw();
//        boolean entityOrDirectionalLightHasMoved = renderState.entityMovedInCycle || renderState.directionalLightNeedsShadowMapRender;
//        if(entityOrDirectionalLightHasMoved)
//        {
//            if only second bounce, clear current target texture
//            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);
//        }
        GPUProfiler.end();
    }

    Vector4f maxExtents = new Vector4f();
    public float getSceneScale(RenderState renderState) {
        maxExtents.x = (Math.max(Math.abs(renderState.getSceneMin().x), Math.abs(renderState.getSceneMax().x)));
        maxExtents.y = (Math.max(Math.abs(renderState.getSceneMin().y), Math.abs(renderState.getSceneMax().y)));
        maxExtents.z = (Math.max(Math.abs(renderState.getSceneMin().z), Math.abs(renderState.getSceneMax().z)));
        float max = Math.max(Math.max(maxExtents.x, maxExtents.y), maxExtents.z);
        float sceneScale = max / (float) gridSizeHalf;
        sceneScale = Math.max(sceneScale, 2.0f);
        boolean sceneScaleChanged = this.sceneScale != sceneScale;
        if(sceneScaleChanged)
        {
            this.sceneScale = sceneScale;
            initOrthoCam();
            initViewXBuffer();
            initViewYBuffer();
            initViewZBuffer();
        }
        return sceneScale;
    }

    private int getGridSizeScaled() {
        return (int)(gridSize*sceneScale);
    }

    private int getGridSizeHalfScaled() {
        return (int)((gridSizeHalf)*sceneScale);
    }

    private void initViewZBuffer() {
        viewZTransform = new SimpleTransform();
        viewZTransform.rotate(new AxisAngle4f(0,1,0, (float) Math.toRadians(180f)));
        viewZ = new Matrix4f(ortho).mul(viewZTransform.getViewMatrix());
        viewZBuffer.rewind();
        viewZ.get(viewZBuffer);
        viewZBuffer.rewind();
    }

    private void initViewYBuffer() {
        viewYTransform = new SimpleTransform();
        viewYTransform.rotate(new AxisAngle4f(1, 0, 0, (float) Math.toRadians(90f)));
        viewY = new Matrix4f(ortho).mul(viewYTransform.getViewMatrix());
        viewYBuffer.rewind();
        viewY.get(viewYBuffer);
        viewYBuffer.rewind();
    }

    private void initViewXBuffer() {
        viewXTransform = new SimpleTransform();
        viewXTransform.rotate(new AxisAngle4f(0,1,0, (float) Math.toRadians(90f)));
        viewX = new Matrix4f(ortho).mul(viewXTransform.getViewMatrix());
        viewXBuffer.rewind();
        viewX.get(viewXBuffer);
        viewXBuffer.rewind();
    }

    private void initOrthoCam() {
        ortho = Util.createOrthogonal(-getGridSizeHalfScaled(), getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled());

        orthoCam.setPerspective(false);
        orthoCam.setWidth(getGridSizeScaled());
        orthoCam.setHeight(getGridSizeScaled());
        orthoCam.setFar(-2000);
        orthoCam.update(engine, 0.000001f);
    }
}
