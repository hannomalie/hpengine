package renderer.rendertarget;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import engine.AppContext;
import renderer.constants.GlTextureTarget;
import util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

public class RenderTarget {
    private boolean useDepthBuffer;
    protected int framebufferLocation = -1;
    protected int depthbufferLocation = -1;
    protected int[] renderedTextures;
    protected int width;
    protected int height;
    protected float clearR;
    protected float clearG;
    protected float clearB;
    protected float clearA;
    protected List<ColorAttachmentDefinition> colorAttachments;
    protected IntBuffer scratchBuffer;

    protected RenderTarget() {
    }

    public RenderTarget(RenderTargetBuilder renderTargetBuilder) {

        AppContext.getInstance().getRenderer().getOpenGLContext().doWithOpenGLContext(() -> {
            width = renderTargetBuilder.width;
            height = renderTargetBuilder.height;
            colorAttachments = renderTargetBuilder.colorAttachments;
            useDepthBuffer = renderTargetBuilder.useDepthBuffer;

            renderedTextures = new int[colorAttachments.size()];
            framebufferLocation = GL30.glGenFramebuffers();

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);

            scratchBuffer = BufferUtils.createIntBuffer(colorAttachments.size());

            for (int i = 0; i < colorAttachments.size(); i++) {
                ColorAttachmentDefinition currentAttachment = colorAttachments.get(i);

                int renderedTextureTemp = GL11.glGenTextures();

                AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, currentAttachment.internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);

                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, currentAttachment.textureFilter);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, util.Util.calculateMipMapCount(Math.max(width,height)));

                FloatBuffer borderColorBuffer = BufferUtils.createFloatBuffer(4);
                float[] borderColors = new float[]{0, 0, 0, 1};
                borderColorBuffer.put(borderColors);
                borderColorBuffer.rewind();
                GL11.glTexParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer);
                GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, renderedTextureTemp, 0);
                scratchBuffer.put(i, GL30.GL_COLOR_ATTACHMENT0 + i);
                renderedTextures[i] = renderedTextureTemp;
            }
            GL20.glDrawBuffers(scratchBuffer);

            if (renderTargetBuilder.useDepthBuffer) {
                depthbufferLocation = GL30.glGenRenderbuffers();
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthbufferLocation);
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_DEPTH_COMPONENT, width, height);
                GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthbufferLocation);
            }

            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                System.out.println("RenderTarget fucked up");
                if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
                    System.out.println("GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT");
                } else if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
                    System.out.println("GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT");
                }
                new RuntimeException().printStackTrace();
                System.exit(0);
            }
        });

        AppContext.getInstance().getRenderer().getOpenGLContext().clearColor(clearR, clearG, clearB, clearA);
    }

    public void resizeTextures() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);

        for (int i = 0; i < colorAttachments.size(); i++) {
            ColorAttachmentDefinition currentAttachment = colorAttachments.get(i);

            int renderedTextureTemp = renderedTextures[i];

            AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, currentAttachment.internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, currentAttachment.textureFilter);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            FloatBuffer borderColorBuffer = BufferUtils.createFloatBuffer(4);
            float[] borderColors = new float[]{0, 0, 0, 1};
            borderColorBuffer.put(borderColors);
            borderColorBuffer.rewind();
            GL11.glTexParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderColorBuffer);
            GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, renderedTextureTemp, 0);
        }
        GL20.glDrawBuffers(scratchBuffer);
    }

    public void resize(int width, int height) {
        setWidth(width);
        setHeight(height);
        resizeTextures();
    }

    public void use(boolean clear) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferLocation);
        AppContext.getInstance().getRenderer().getOpenGLContext().viewPort(0, 0, width, height);
        if (clear) {
            AppContext.getInstance().getRenderer().getOpenGLContext().clearDepthAndColorBuffer();
        }
    }

    public void setTargetTexture(int textureID, int index) {
        setTargetTexture(textureID, index, 0);
    }

    public void setTargetTexture(int textureID, int index, int mipMapLevel) {
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + index, textureID, mipMapLevel);
    }

    /**
     * @param attachmentIndex the attachment point index
     * @param textureId       the id of the cubemap texture
     * @param index           the index of the cubemap face, from 0 to 5
     * @param mipmap          the mipmap level that should be bound
     */
    public void setCubeMapFace(int attachmentIndex, int textureId, int index, int mipmap) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + attachmentIndex, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, textureId, mipmap);
    }

    public void unuse() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public int getRenderedTexture(int index) {
        return renderedTextures[index];
    }

    public int getDepthBufferTexture() {
        return depthbufferLocation;
    }

    public void setRenderedTexture(int renderedTexture, int index) {
        this.renderedTextures[index] = renderedTexture;
    }

    public void saveBuffer(String path) {
        Util.saveImage(getBuffer(), path);
    }

    public void saveDepthBuffer(String path) {
        Util.saveImage(getDepthBuffer(), path);
    }

    public BufferedImage getBuffer() {
        return Util.toImage(getTargetTexturedata(), width, height);
    }

    public BufferedImage getDepthBuffer() {
        return Util.toImage(getDepthTexturedata(), width, height);
    }

    public ByteBuffer getTargetTexturedata() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);

        return pixels;
    }


    /**
     * @return a byte buffer with the depth texture data or null if this rendertarget doesn't use depth attachment
     */
    public ByteBuffer getDepthTexturedata() {
        if(!useDepthBuffer) { return null; }
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixels);

        return pixels;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getRenderedTexture() {
        return getRenderedTexture(0);
    }

    public int getFrameBufferLocation() {
        return framebufferLocation;
    }

    public void setTargetTextureArrayIndex(int textureArray, int textureIndex) {
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, textureArray, 0, textureIndex);
    }
}
