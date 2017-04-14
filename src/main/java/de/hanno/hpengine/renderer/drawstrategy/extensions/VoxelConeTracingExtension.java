package de.hanno.hpengine.renderer.drawstrategy.extensions;

import com.carrotsearch.hppc.ObjectLongHashMap;
import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.PerMeshInfo;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.Pipeline;
import de.hanno.hpengine.util.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.shader.ComputeShaderProgram;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;

import static de.hanno.hpengine.renderer.constants.GlCap.*;
import static de.hanno.hpengine.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.renderer.constants.GlTextureTarget.TEXTURE_3D;

public class VoxelConeTracingExtension implements RenderExtension {
    public static final int gridSize = 256;
    public static final int gridSizeHalf = gridSize/2;
    public static final int gridTextureFormat = GL11.GL_RGBA;//GL11.GL_R;
    public static final int gridTextureFormatSized = GL11.GL_RGBA8;//GL30.GL_R32UI;
    private Transform viewXTransform;
    private Transform viewYTransform;
    private Transform viewZTransform;

    public float sceneScale = 2;

    private Matrix4f ortho = de.hanno.hpengine.util.Util.createOrthogonal(-getGridSizeHalfScaled(), getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled());
    private Camera orthoCam = new Camera(ortho, getGridSizeHalfScaled(), -getGridSizeHalfScaled(), 90, 1);
    private Matrix4f viewX;
    public FloatBuffer viewXBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f viewY;
    public FloatBuffer viewYBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f viewZ;
    public FloatBuffer viewZBuffer = BufferUtils.createFloatBuffer(16);

    static FloatBuffer zeroBuffer = BufferUtils.createFloatBuffer(4);
    static {
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.rewind();
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

    private Pipeline pipeline;
    private FirstPassResult firstPassResult = new FirstPassResult();
    private boolean useIndirectDrawing = false;

    public VoxelConeTracingExtension() throws Exception {
        initOrthoCam();
        initViewXBuffer();
        initViewYBuffer();
        initViewZBuffer();

        voxelizer = ProgramFactory.getInstance().getProgram("voxelize_vertex.glsl", "voxelize_geometry.glsl", "voxelize_fragment.glsl", true);
        texture3DMipMapAlphaBlendComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_mipmap_alphablend_compute.glsl");
        texture3DMipMapComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_mipmap_compute.glsl");
        clearDynamicVoxelsComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_clear_dynamic_voxels_compute.glsl");
        injectLightComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_inject_light_compute.glsl");
        injectMultipleBounceLightComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_inject_bounce_light_compute.glsl");

        grid = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        gridTwo = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        albedoGrid = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        normalGrid = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_NEAREST,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);

        currentVoxelTarget = grid;
        currentVoxelSource = gridTwo;
        voxelConeTraceProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "voxel_cone_trace_fragment.glsl");
        Config.getInstance().setUseAmbientOcclusion(false);
        pipeline = new Pipeline(false, false, false);
    }

    private long entityMovedLastInCycle;
    private long directionalLightMovedLastInCycle;

    @Override
    public void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {
        GPUProfiler.start("VCT first pass");
        boolean entityMoved = renderState.entitiesState.entityMovedInCycle > entityMovedLastInCycle;
        boolean directionalLightMoved = renderState.directionalLightHasMovedInCycle > directionalLightMovedLastInCycle;
        if(entityMoved) {
            entityMovedLastInCycle = renderState.getCycle();
        }
        if(directionalLightMoved) {
            directionalLightMovedLastInCycle = renderState.getCycle();
            lightInjectedCounter = 0;
        }
        boolean useVoxelConeTracing = true;
        boolean clearVoxels = true;
        int bounces = 1;

        boolean needsRevoxelization = useVoxelConeTracing && (!renderState.sceneInitiallyDrawn || Config.getInstance().isForceRevoxelization() || renderState.perEntityInfos().stream().anyMatch(info -> info.getUpdate().equals(Entity.Update.DYNAMIC)));
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
            GraphicsContext.getInstance().bindTexture(1, TEXTURE_3D, albedoGrid);
            GraphicsContext.getInstance().bindTexture(2, TEXTURE_3D, normalGrid);
            GraphicsContext.getInstance().bindTexture(3, TEXTURE_3D, currentVoxelSource);
            int num_groups_xyz = Math.max(gridSize / 8, 1);

            if(lightInjectedFramesAgo == 0)
            {
                injectLightComputeProgram.use();
                injectLightComputeProgram.setUniform("bounces", bounces);
                injectLightComputeProgram.setUniform("sceneScale", getSceneScale(renderState));
                injectLightComputeProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
                injectLightComputeProgram.setUniform("gridSize", gridSize);
                injectLightComputeProgram.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
                injectLightComputeProgram.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection);
                injectLightComputeProgram.setUniform("lightColor", renderState.directionalLightState.directionalLightColor);

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
            if(Config.getInstance().isForceRevoxelization() || !renderState.sceneInitiallyDrawn) {
                ARBClearTexture.glClearTexImage(currentVoxelTarget, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                ARBClearTexture.glClearTexImage(normalGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                ARBClearTexture.glClearTexImage(albedoGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
            } else {
                clearDynamicVoxelsComputeProgram.use();
                int num_groups_xyz = Math.max(gridSize / 8, 1);
                GL42.glBindImageTexture(0, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                GL42.glBindImageTexture(1, normalGrid, 0, true, 0, GL15.GL_READ_WRITE, gridTextureFormatSized);
                GL42.glBindImageTexture(3, currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                GraphicsContext.getInstance().bindTexture(4, TEXTURE_3D, normalGrid);
                clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz,num_groups_xyz,num_groups_xyz);
            }
            GPUProfiler.end();
        }

        if(needsRevoxelization) {
            GPUProfiler.start("Voxelization");
            orthoCam.update(0.000001f);
            float sceneScale = getSceneScale(renderState);
            int gridSizeScaled = (int) (gridSize * sceneScale);
            GraphicsContext.getInstance().viewPort(0, 0, gridSizeScaled, gridSizeScaled);
            voxelizer.use();
            GL42.glBindImageTexture(3, normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            GL42.glBindImageTexture(5, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            voxelizer.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
            voxelizer.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection);
            voxelizer.setUniform("lightColor", renderState.directionalLightState.directionalLightColor);
            voxelizer.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
            voxelizer.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
            voxelizer.setUniformAsMatrix4("u_MVPx", viewXBuffer);
            voxelizer.setUniformAsMatrix4("u_MVPy", viewYBuffer);
            voxelizer.setUniformAsMatrix4("u_MVPz", viewZBuffer);
            FloatBuffer viewMatrixAsBuffer1 = orthoCam.getViewMatrixAsBuffer();
            voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
            FloatBuffer projectionMatrixAsBuffer1 = orthoCam.getProjectionMatrixAsBuffer();
            voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
            voxelizer.setUniform("u_width", gridSize);
            voxelizer.setUniform("u_height", gridSize);

            voxelizer.setUniform("writeVoxels", true);
            voxelizer.setUniform("sceneScale", sceneScale);
            voxelizer.setUniform("inverseSceneScale", 1f / sceneScale);
            voxelizer.setUniform("gridSize", gridSize);
            GraphicsContext.getInstance().depthMask(false);
            GraphicsContext.getInstance().disable(DEPTH_TEST);
            GraphicsContext.getInstance().disable(BLEND);
            GraphicsContext.getInstance().disable(CULL_FACE);
            GL11.glColorMask(false, false, false, false);


            if(useIndirectDrawing && Config.getInstance().isIndirectDrawing()) {
                firstPassResult.reset();
                pipeline.prepareAndDraw(renderState, voxelizer, firstPassResult);
            } else {
                for (PerMeshInfo entity : renderState.perEntityInfos()) {
                    boolean isStatic = entity.getUpdate().equals(Entity.Update.STATIC);
                    if (renderState.sceneInitiallyDrawn && !Config.getInstance().isForceRevoxelization() && isStatic) {
                        continue;
                    }
                    int currentVerticesCount = DrawStrategy.draw(renderState.getVertexIndexBuffer().getVertexBuffer(), renderState.getVertexIndexBuffer().getIndexBuffer(), entity, voxelizer, false);

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
        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        GraphicsContext.getInstance().bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        GraphicsContext.getInstance().bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        GraphicsContext.getInstance().bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        GraphicsContext.getInstance().bindTexture(7, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        GraphicsContext.getInstance().bindTexture(12, TEXTURE_3D, albedoGrid);
        GraphicsContext.getInstance().bindTexture(13, TEXTURE_3D, currentVoxelSource);
        GraphicsContext.getInstance().bindTexture(14, TEXTURE_3D, normalGrid);

        voxelConeTraceProgram.use();
        voxelConeTraceProgram.setUniform("eyePosition", renderState.camera.getWorldPosition());
        voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        voxelConeTraceProgram.bindShaderStorageBuffer(0, Renderer.getInstance().getGBuffer().getStorageBuffer());
        voxelConeTraceProgram.setUniform("sceneScale", getSceneScale(renderState));
        voxelConeTraceProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
        voxelConeTraceProgram.setUniform("gridSize", gridSize);
        voxelConeTraceProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
        voxelConeTraceProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        voxelConeTraceProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
        QuadVertexBuffer.getFullscreenBuffer().draw();
//        boolean entityOrDirectionalLightHasMoved = renderState.entityMovedInCycle || renderState.directionalLightNeedsShadowMapRender;
//        if(entityOrDirectionalLightHasMoved)
//        {
//            if only second bounce, clear current target texture
//            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
//        }
        GPUProfiler.end();
    }

    Vector4f maxExtents = new Vector4f();
    public float getSceneScale(RenderState renderState) {
        maxExtents.setX(Math.max(Math.abs(renderState.sceneMin.x), Math.abs(renderState.sceneMax.x)));
        maxExtents.setY(Math.max(Math.abs(renderState.sceneMin.y), Math.abs(renderState.sceneMax.y)));
        maxExtents.setZ(Math.max(Math.abs(renderState.sceneMin.z), Math.abs(renderState.sceneMax.z)));
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
        viewZTransform = new Transform();
        viewZTransform.rotate(new Vector3f(0,1,0), 180f);
        viewZ = Matrix4f.mul(ortho, viewZTransform.getViewMatrix(), null);
        viewZBuffer.rewind();
        viewZ.store(viewZBuffer);
        viewZBuffer.rewind();
    }

    private void initViewYBuffer() {
        viewYTransform = new Transform();
        viewYTransform.rotate(new Vector3f(1, 0, 0), 90f);
        viewY = Matrix4f.mul(ortho, viewYTransform.getViewMatrix(), null);
        viewYBuffer.rewind();
        viewY.store(viewYBuffer);
        viewYBuffer.rewind();
    }

    private void initViewXBuffer() {
        viewXTransform = new Transform();
        viewXTransform.rotate(new Vector3f(0,1,0), 90f);
        viewX = Matrix4f.mul(ortho, viewXTransform.getViewMatrix(), null);
        viewXBuffer.rewind();
        viewX.store(viewXBuffer);
        viewXBuffer.rewind();
    }

    private void initOrthoCam() {
        ortho = Util.createOrthogonal(-getGridSizeHalfScaled(), getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled());
        orthoCam = new Camera(ortho, getGridSizeHalfScaled(), -getGridSizeHalfScaled(), 90, 1);

        orthoCam.setPerspective(false);
        orthoCam.setWidth(getGridSizeScaled());
        orthoCam.setHeight(getGridSizeScaled());
        orthoCam.setFar(-5000);
        orthoCam.update(0.000001f);
    }
}
