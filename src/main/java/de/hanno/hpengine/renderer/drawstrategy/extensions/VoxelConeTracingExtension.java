package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.util.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.RenderState;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.scene.Scene;
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

    @Override
    public void renderFirstPass(RenderState renderState, FirstPassResult firstPassResult) {
        GPUProfiler.start("VCT first pass");
        boolean directionalLightMoved = renderState.directionalLightNeedsShadowMapRender;
        boolean entityOrDirectionalLightHasMoved = renderState.anEntityHasMoved || directionalLightMoved;
        boolean useVoxelConeTracing = true;
        boolean clearVoxels = true;
        Scene scene = Engine.getInstance().getScene();
        int bounces = 4;
        Integer lightInjectedFramesAgo = (Integer) firstPassResult.getProperty("vctLightInjectedFramesAgo");
        if(lightInjectedFramesAgo == null) {
            lightInjectedFramesAgo = 0;
        }
        boolean sceneContainsDynamicObjects = renderState.perEntityInfos().stream().anyMatch(e -> e.getUpdate().equals(Entity.Update.DYNAMIC));
        boolean needsRevoxelization = (useVoxelConeTracing && (entityOrDirectionalLightHasMoved)) || !renderState.sceneInitiallyDrawn;
        boolean needsLightInjection = lightInjectedFramesAgo < bounces-1;
        if(entityOrDirectionalLightHasMoved) {
            lightInjectedFramesAgo = 0;
        }
        if(needsRevoxelization || needsLightInjection)
        {
            if(clearVoxels && renderState.anEntityHasMoved) {
                GPUProfiler.start("Clear voxels");
                if(!renderState.sceneInitiallyDrawn) {
                    ARBClearTexture.glClearTexImage(currentVoxelTarget, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                    ARBClearTexture.glClearTexImage(normalGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                    ARBClearTexture.glClearTexImage(albedoGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                } else {
                    clearDynamicVoxelsComputeProgram.use();
                    int num_groups_xyz = Math.max(gridSize / 8, 1);
                    GL42.glBindImageTexture(0, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                    GL42.glBindImageTexture(1, normalGrid, 0, true, 0, GL15.GL_READ_WRITE, gridTextureFormatSized);
//                GL42.glBindImageTexture(2, currentVoxelSource, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                    GL42.glBindImageTexture(3, currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                    OpenGLContext.getInstance().bindTexture(4, TEXTURE_3D, normalGrid);
                    clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz,num_groups_xyz,num_groups_xyz);
                }
                GPUProfiler.end();
            }

            if(needsRevoxelization) {
                GPUProfiler.start("Voxelization");
                orthoCam.update(0.000001f);
                int gridSizeScaled = (int) (gridSize * getSceneScale(renderState));
                OpenGLContext.getInstance().viewPort(0, 0, gridSizeScaled, gridSizeScaled);
                voxelizer.use();
                GL42.glBindImageTexture(3, normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                GL42.glBindImageTexture(5, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                OpenGLContext.getInstance().bindTexture(8, TEXTURE_3D, currentVoxelSource);
                if (scene != null) {
                    voxelizer.setUniformAsMatrix4("shadowMatrix", renderState.directionalLight.getViewProjectionMatrixAsBuffer());
                    voxelizer.setUniform("lightDirection", renderState.directionalLight.getDirection());
                    voxelizer.setUniform("lightColor", renderState.directionalLight.getColor());
                }
                voxelizer.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
                voxelizer.bindShaderStorageBuffer(3, EntityFactory.getInstance().getEntitiesBuffer());
                voxelizer.setUniformAsMatrix4("u_MVPx", viewXBuffer);
                voxelizer.setUniformAsMatrix4("u_MVPy", viewYBuffer);
                voxelizer.setUniformAsMatrix4("u_MVPz", viewZBuffer);
                FloatBuffer viewMatrixAsBuffer1 = renderState.camera.getViewMatrixAsBuffer();
                voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
                FloatBuffer projectionMatrixAsBuffer1 = renderState.camera.getProjectionMatrixAsBuffer();
                voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
                voxelizer.setUniform("u_width", gridSize);
                voxelizer.setUniform("u_height", gridSize);

                voxelizer.setUniform("writeVoxels", true);
                voxelizer.setUniform("sceneScale", getSceneScale(renderState));
                voxelizer.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
                voxelizer.setUniform("gridSize", gridSize);
                voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
                voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
                OpenGLContext.getInstance().depthMask(false);
                OpenGLContext.getInstance().disable(DEPTH_TEST);
                OpenGLContext.getInstance().disable(BLEND);
                OpenGLContext.getInstance().disable(CULL_FACE);
                GL11.glColorMask(false, false, false, false);

                for (PerEntityInfo entity : renderState.perEntityInfos()) {
                    boolean isStatic = entity.getUpdate().equals(Entity.Update.STATIC);
                    if (renderState.sceneInitiallyDrawn && isStatic) {
                        continue;
                    }
                    if(!entityOrDirectionalLightHasMoved) { continue; }
                    lightInjectedFramesAgo = 0;
                    voxelizer.setUniform("isStatic", isStatic ? 1 : 0);
                    int currentVerticesCount = DrawStrategy.draw(renderState, entity);

                    firstPassResult.verticesDrawn += currentVerticesCount;
                    if (currentVerticesCount > 0) {
                        firstPassResult.entitiesDrawn++;
                    }
                }
                GPUProfiler.end();
            }

            if(needsLightInjection) {
                GPUProfiler.start("grid shading");
                GL42.glBindImageTexture(0, currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                OpenGLContext.getInstance().bindTexture(1, TEXTURE_3D, albedoGrid);
                OpenGLContext.getInstance().bindTexture(2, TEXTURE_3D, normalGrid);
                OpenGLContext.getInstance().bindTexture(3, TEXTURE_3D, currentVoxelSource);
                int num_groups_xyz = Math.max(gridSize / 8, 1);

                if(lightInjectedFramesAgo == 0)
                {
                    injectLightComputeProgram.use();
                    injectLightComputeProgram.setUniform("bounces", bounces);
                    injectLightComputeProgram.setUniform("sceneScale", getSceneScale(renderState));
                    injectLightComputeProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
                    injectLightComputeProgram.setUniform("gridSize", gridSize);
                    if (scene != null) {
                        injectLightComputeProgram.setUniformAsMatrix4("shadowMatrix", renderState.directionalLight.getViewProjectionMatrixAsBuffer());
                        injectLightComputeProgram.setUniform("lightDirection", renderState.directionalLight.getCamera().getViewDirection());
                        injectLightComputeProgram.setUniform("lightColor", renderState.directionalLight.getColor());
                    }

                    injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
                }
                else {
                    injectMultipleBounceLightComputeProgram.use();
                    injectMultipleBounceLightComputeProgram.setUniform("bounces", bounces);
                    injectMultipleBounceLightComputeProgram.setUniform("lightInjectedFramesAgo", lightInjectedFramesAgo);
                    injectMultipleBounceLightComputeProgram.setUniform("sceneScale", getSceneScale(renderState));
                    injectMultipleBounceLightComputeProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
                    injectMultipleBounceLightComputeProgram.setUniform("gridSize", gridSize);
                    injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
                }
                mipmapGrid();
                firstPassResult.setProperty("vctLightInjectedFramesAgo", lightInjectedFramesAgo+1);
                GPUProfiler.end();
                switchCurrentVoxelGrid();
            } else {
                firstPassResult.setProperty("vctLightInjectedFramesAgo", lightInjectedFramesAgo);
            }

            GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
            GL11.glColorMask(true, true, true, true);
        }
        GPUProfiler.end();
    }

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

        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        GPUProfiler.start("VCT second pass");
        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        OpenGLContext.getInstance().bindTexture(7, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        OpenGLContext.getInstance().bindTexture(12, TEXTURE_3D, albedoGrid);
        OpenGLContext.getInstance().bindTexture(13, TEXTURE_3D, currentVoxelSource);
        OpenGLContext.getInstance().bindTexture(14, TEXTURE_3D, normalGrid);

        voxelConeTraceProgram.use();
        voxelConeTraceProgram.setUniform("eyePosition", renderState.camera.getWorldPosition());
        voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        voxelConeTraceProgram.bindShaderStorageBuffer(0, Renderer.getInstance().getGBuffer().getStorageBuffer());
        voxelConeTraceProgram.setUniform("sceneScale", getSceneScale(renderState));
        voxelConeTraceProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderState));
        voxelConeTraceProgram.setUniform("gridSize", gridSize);
        voxelConeTraceProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
        voxelConeTraceProgram.setUniform("screenWidth", (float) Config.WIDTH);
        voxelConeTraceProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        QuadVertexBuffer.getFullscreenBuffer().draw();
//        boolean entityOrDirectionalLightHasMoved = renderState.anEntityHasMoved || renderState.directionalLightNeedsShadowMapRender;
//        if(entityOrDirectionalLightHasMoved)
//        {
//            if only second bounce, clear current target de.hanno.hpengine.texture
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
        if(sceneScaleChanged) {
            initOrthoCam();
            initViewXBuffer();
            initViewYBuffer();
            initViewZBuffer();
        }
        this.sceneScale = sceneScale;
        return sceneScale;
    }

    private int getGridSizeScaled() {
        return (int)(gridSize*sceneScale);
    }

    private int getGridSizeHalfScaled() {
        return (int)((gridSizeHalf)*sceneScale);
    }
}
