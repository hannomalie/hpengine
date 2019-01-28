package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.graphics.PerFrameCommandProvider;
import de.hanno.hpengine.engine.graphics.GpuCommandSync;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.constants.*;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.threads.TimeStepThread;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import de.hanno.hpengine.util.commandqueue.FutureCallable;

import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class MockContext implements GpuContext {

    @Override
    public RenderTarget getFrontBuffer() {
        return null;
    }

    @Override
    public long getWindowHandle() {
        return 0;
    }

    @Override
    public int getCanvasWidth() {
        return 0;
    }

    @Override
    public int getCanvasHeight() {
        return 0;
    }

    @Override
    public void setCanvasWidth(int width) {

    }

    @Override
    public void setCanvasHeight(int height) {

    }

    @Override
    public void createNewGPUFenceForReadState(RenderState currentReadState) {

    }

    @Override
    public void registerPerFrameCommand(PerFrameCommandProvider perFrameCommandProvider) {

    }

    @Override
    public boolean isError() {
        return false;
    }

    @Override
    public void update(float seconds) {

    }

    @Override
    public void enable(GlCap cap) {

    }

    @Override
    public void disable(GlCap cap) {

    }

    @Override
    public void activeTexture(int textureUnitIndex) {

    }

    @Override
    public void bindTexture(GlTextureTarget target, int textureId) {

    }

    @Override
    public void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId) {

    }

    @Override
    public void bindTextures(IntBuffer textureIds) {

    }

    @Override
    public void bindTextures(int count, IntBuffer textureIds) {

    }

    @Override
    public void bindTextures(int firstUnit, int count, IntBuffer textureIds) {

    }

    @Override
    public void viewPort(int x, int y, int width, int height) {

    }

    @Override
    public void clearColorBuffer() {

    }

    @Override
    public void clearDepthBuffer() {

    }

    @Override
    public void clearDepthAndColorBuffer() {

    }

    @Override
    public void bindFrameBuffer(int frameBuffer) {

    }

    @Override
    public void depthMask(boolean flag) {

    }

    @Override
    public void depthFunc(GlDepthFunc func) {

    }

    @Override
    public void readBuffer(int colorAttachmentIndex) {

    }

    @Override
    public void blendEquation(BlendMode mode) {

    }

    @Override
    public void blendFunc(BlendMode.Factor sfactor, BlendMode.Factor dfactor) {

    }

    @Override
    public void cullFace(CullMode mode) {

    }

    @Override
    public void clearColor(float r, float g, float b, float a) {

    }

    @Override
    public void bindImageTexture(int unit, int textureId, int level, boolean layered, int layer, int access, int internalFormat) {

    }

    @Override
    public int genTextures() {
        return 0;
    }

    @Override
    public int getAvailableVRAM() {
        return 0;
    }

    @Override
    public int getAvailableTotalVRAM() {
        return 0;
    }

    @Override
    public int getDedicatedVRAM() {
        return 0;
    }

    @Override
    public int getEvictedVRAM() {
        return 0;
    }

    @Override
    public int getEvictionCount() {
        return 0;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void execute(Runnable runnable) {

    }

    @Override
    public void execute(Runnable runnable, boolean andBlock) {

    }

    @Override
    public <RETURN_TYPE> RETURN_TYPE calculate(Callable<RETURN_TYPE> callable) {
        return null;
    }

    @Override
    public <RETURN_TYPE> CompletableFuture<RETURN_TYPE> execute(FutureCallable<RETURN_TYPE> command) {
        return null;
    }

    @Override
    public long blockUntilEmpty() {
        return 0;
    }

    @Override
    public TimeStepThread getGpuThread() {
        return null;
    }

    @Override
    public int createProgramId() {
        return 0;
    }

    @Override
    public int getMaxTextureUnits() {
        return 0;
    }

    @Override
    public CommandQueue getCommandQueue() {
        return null;
    }

    @Override
    public void benchmark(Runnable runnable) {

    }

    @Override
    public void destroy() {

    }

    @Override
    public VertexBuffer getFullscreenBuffer() {
        return null;
    }

    @Override
    public VertexBuffer getDebugBuffer() {
        return null;
    }

    @Override
    public int genFrameBuffer() {
        return 0;
    }

    @Override
    public void clearCubeMap(int i, int textureFormat) {

    }

    @Override
    public void clearCubeMapInCubeMapArray(int textureID, int internalFormat, int width, int height, int cubeMapIndex) {

    }

    @Override
    public void register(RenderTarget target) {

    }

    @Override
    public List<RenderTarget> getRegisteredRenderTargets() {
        return null;
    }

    @Override
    public void finishFrame(RenderState renderState) {

    }

    @Override
    public void pollEvents() {

    }
}
