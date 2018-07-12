package de.hanno.hpengine.engine.model.texture;

import ddsutil.DDSUtil;
import ddsutil.ImageRescaler;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.ressources.Reloadable;
import jogl.DDSImage;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.engine.model.texture.Texture.UploadState.*;
import static org.lwjgl.opengl.GL15.GL_STREAM_COPY;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;

public class Texture implements Reloadable {
    private static final Logger LOGGER = Logger.getLogger(Texture.class.getName());

    protected final TextureManager textureManager;
    protected boolean srgba;
    protected String path = "";
    private volatile boolean mipmapsGenerated = false;
    private boolean sourceDataCompressed = false;
    private long lastUsedTimeStamp = System.currentTimeMillis();
    private volatile boolean preventUnload = false;

    public void setUsedNow() {
        if(NOT_UPLOADED.equals(uploadState)) {
            load();
        } else if(UPLOADED.equals(uploadState) || UPLOADING.equals(uploadState)) {
            lastUsedTimeStamp = System.currentTimeMillis();
        }
    }

    public long getLastUsedTimeStamp() {
        return lastUsedTimeStamp;
    }

    public UploadState getUploadState() {
        return uploadState;
    }

    public void setData(byte[][] bytes) {
        data = bytes;
    }

    private volatile UploadState uploadState = NOT_UPLOADED;

	protected GlTextureTarget target = TEXTURE_2D;
    protected int textureID = -1;
    protected int height;
    protected int width;
    protected volatile byte[][] data;
    protected int dstPixelFormat = GL11.GL_RGBA;
    protected int srcPixelFormat = GL11.GL_RGBA;
    protected int minFilter = GL11.GL_LINEAR;
    protected int magFilter = GL11.GL_LINEAR;
    private int mipmapCount = -1;
    protected long handle =-1L;


    protected Texture(TextureManager textureManager) {
        this.textureManager = textureManager;
        genHandle(textureManager);
    }

    protected void genHandle(TextureManager textureManager) {
        if(handle <= 0) {
            handle = textureManager.getGpuContext().calculate(() -> {
                bind(15);
                long theHandle = ARBBindlessTexture.glGetTextureHandleARB(textureID);
                ARBBindlessTexture.glMakeTextureHandleResidentARB(theHandle);
                unbind(15);
                return theHandle;
            });
        }
    }

    Texture(TextureManager textureManager, String path, boolean srgba, int width, int height, int mipMapCount, boolean mipmapsGenerated, int srcPixelFormat, boolean sourceDataCompressed, byte[][] data) {
        this.target = TEXTURE_2D;
        this.path = path;
        this.srgba = srgba;
        this.textureManager = textureManager;
        this.width = width;
        this.height = height;
        this.mipmapCount = mipMapCount;
        this.mipmapsGenerated = mipmapsGenerated;
        this.srcPixelFormat = srcPixelFormat;
        this.sourceDataCompressed = sourceDataCompressed;
        this.data = data;

    }

    Texture(TextureManager textureManager, String path, GlTextureTarget target, boolean srgba) {
        this.target = target;
        this.path = path;
        this.srgba = srgba;
        this.textureManager = textureManager;
    }

    /**
     * Bind the specified GL context to a de.hanno.hpengine.texture
     *
     */
    public void bind() {
        bind(true);
    }
    public void bind(boolean setUsed) {
        if(textureID <= 0) {
//            LOGGER.info("de.hanno.hpengine.texture id is <= 0");
            textureID = textureManager.getGpuContext().genTextures();
        }
        if(setUsed) {
            setUsedNow();
        }
        textureManager.getGpuContext().bindTexture(target, textureID);
    }

    public void bind(int unit) {
        bind(unit, true);
    }
    public void bind(int unit, boolean setUsed) {
        if(textureID <= 0) {
            textureID = textureManager.getGpuContext().genTextures();
        }
        if(setUsed) {
            setUsedNow();
        }
        textureManager.getGpuContext().bindTexture(unit, target, textureID);
    }
    public void  unbind(int unit) {
        textureManager.getGpuContext().bindTexture(unit, target, 0);
    }

    public void setHeight(int height) {
        this.height = height;
    }
    public void setWidth(int width) {
        this.width = width;
    }
    
    public int getHeight() {
        return height;
    }
    public int getWidth() {
        return width;
    }

	public void setDstPixelFormat(int dstPixelFormat) {
		this.dstPixelFormat = dstPixelFormat;
	}

	public void setSrcPixelFormat(int srcPixelFormat) {
		this.srcPixelFormat = srcPixelFormat;
	}

    public ByteBuffer buffer() {
        ByteBuffer imageBuffer = ByteBuffer.allocateDirect(data[0].length);
        imageBuffer.put(data[0], 0, data[0].length);
        imageBuffer.flip();
        return imageBuffer;
    }
    public static ByteBuffer buffer(byte[] data) {
        ByteBuffer imageBuffer = BufferUtils.createByteBuffer(data.length);
        imageBuffer.put(data, 0, data.length);
        imageBuffer.flip();
        return imageBuffer;
    }

	public void upload(TextureManager textureManager, boolean srgba) {
        this.srgba = srgba;
        if(data == null || data[0] == null) { return; }
        upload(textureManager, buffer(), srgba);
	}

	public void upload(TextureManager textureManager, ByteBuffer textureBuffer) {
		upload(textureManager, textureBuffer, false);
	}
	
	public void upload(TextureManager textureManager, ByteBuffer textureBuffer, boolean srgba) {
        boolean doesntNeedUpload = UPLOADING.equals(uploadState) || UPLOADED.equals(uploadState);
        if(doesntNeedUpload) { return; }

        uploadState = UPLOADING;

        Runnable uploadRunnable = () -> {
            LOGGER.info("Uploading " + path);
            int internalformat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
            if (srgba) {
                internalformat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
            }
            int finalInternalformat = internalformat;

            textureManager.getGpuContext().execute(() -> {
                bind(15);
                if (target == TEXTURE_2D) {
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                    GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0);
                    GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, Util.calculateMipMapCount(Math.max(width, height)));
                }
                unbind(15);
            });
            LOGGER.info("Actually uploading...");
            if (mipmapsGenerated) {
                LOGGER.info("Mipmaps already generated");
                uploadMipMaps(finalInternalformat);
            }
            uploadWithPixelBuffer(textureBuffer, finalInternalformat, getWidth(), getHeight(), 0, sourceDataCompressed, false);
            textureManager.getGpuContext().execute(() -> {
                if(!mipmapsGenerated) {
                    bind(15);
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                    unbind(15);

                }
            });
            genHandle(textureManager);
            setUploaded();
            textureManager.postTextureChangedEvent();
        };

        textureManager.getCommandQueue().addCommand(uploadRunnable);
    }

    private void setUploaded() {
        uploadState = UPLOADED;
        LOGGER.info("Upload finished");
        LOGGER.fine("Free VRAM: " + textureManager.getGpuContext().getAvailableVRAM());
    }

    private void uploadWithPixelBuffer(ByteBuffer textureBuffer, int internalformat, int width, int height, int mipLevel, boolean sourceDataCompressed, boolean setMaxLevel) {
        textureBuffer.rewind();
        final AtomicInteger pbo = new AtomicInteger(-1);
        ByteBuffer temp = textureManager.getGpuContext().calculate(() -> {
            bind(15);
            pbo.set(GL15.glGenBuffers());
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo.get());
            glBufferData(GL_PIXEL_UNPACK_BUFFER, textureBuffer.capacity(), GL_STREAM_COPY);
            ByteBuffer xxx = GL15.glMapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, null);
            unbind(15);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            return xxx;
        });
        temp.put(textureBuffer);
        textureManager.getGpuContext().execute(() -> {
            bind(15);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo.get());
            GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);

            if (sourceDataCompressed) {
                GL13.glCompressedTexImage2D(target.glTarget, mipLevel, internalformat, width, height, 0, textureBuffer.capacity(), 0);
            } else {
                GL11.glTexImage2D(target.glTarget, mipLevel, internalformat, width, height, 0, srcPixelFormat, GL11.GL_UNSIGNED_BYTE, 0);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL15.glDeleteBuffers(pbo.get());
            int textureMaxLevel = mipmapCount - mipLevel;
            if(setMaxLevel) {
                LOGGER.info("TextureMaxLevel: " + Math.max(0, textureMaxLevel));
                GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, textureMaxLevel);
            }
            unbind(15);

            if(mipmapsGenerated && mipLevel == 0) {
                setUploaded();
            }
        });
    }

    private void uploadMipMaps(int internalformat) {
        LOGGER.info("Uploading mipmaps for " + path);
        int currentWidth = width;
        int currentHeight = height;
        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for(int i = 0; i < mipmapCount-1; i++) {
            int minSize = 1;
            currentWidth = Math.max(minSize, currentWidth/2);
            currentHeight = Math.max(minSize, currentHeight/2);
            widths.add(currentWidth);
            heights.add(currentHeight);
        }
        for(int mipmapIndex = mipmapCount-1; mipmapIndex >= 1; mipmapIndex--) {
            currentWidth = widths.get(mipmapIndex - 1);
            currentHeight = heights.get(mipmapIndex - 1);
            ByteBuffer tempBuffer = BufferUtils.createByteBuffer(data[mipmapIndex].length);
            tempBuffer.rewind();
            tempBuffer.put(data[mipmapIndex]);
            tempBuffer.rewind();
            LOGGER.info("Mipmap buffering with " + tempBuffer.remaining() + " remaining bytes for " + currentWidth + " x " +  currentHeight);
            uploadWithPixelBuffer(tempBuffer, internalformat, currentWidth, currentHeight, mipmapIndex, sourceDataCompressed, true);
        }
    }

	public void setMinFilter(int minFilter) {
		this.minFilter = minFilter;
	}

	public void setMagFilter(int magFilter) {
		this.magFilter = magFilter;
	}

    public byte[] getData() {
        return getData(0);
    }
    public byte[] getData(int index) {
        while(uploadState != UPLOADED) {}
        return data[index];
    }

	public static String getDirectory() {
		return DirectoryManager.WORKDIR_NAME + "/assets/textures/";
	}
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public String toString() {
		return "(Texture)" + path;
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}

    public int getTextureID() {
		return textureID;
	}

	public static boolean filterRequiresMipmaps(int magTextureFilter) {
		return (magTextureFilter == GL11.GL_LINEAR_MIPMAP_LINEAR ||
				magTextureFilter == GL11.GL_LINEAR_MIPMAP_NEAREST ||
				magTextureFilter == GL11.GL_NEAREST_MIPMAP_LINEAR ||
				magTextureFilter == GL11.GL_NEAREST_MIPMAP_NEAREST);
	}

    public static boolean textureAvailableAsDDS(String resourceName) {
        String fullPathAsDDS = getFullPathAsDDS(resourceName);
        File f = new File(fullPathAsDDS);
        boolean ddsExists = f.exists();
        return ddsExists;
    }
    public static String getFullPathAsDDS(String fileName) {
        String name = FilenameUtils.getBaseName(fileName);
        String nameWithExtension = FilenameUtils.getName(fileName);//FilenameUtils.getName(fileName) + "." + extension;
        String restPath = fileName.replaceAll(nameWithExtension, "");
        if(restPath == null) {
            restPath = "";
        }
        return restPath + name + ".dds";
    }
    protected static int getNextPowerOfTwo(int fold) {
        int ret = 2;
        while (ret < fold) {
            ret *= 2;
        }
        return ret;
    }

    private static ReentrantLock DDSUtilWriteLock = new ReentrantLock();
    public static final boolean autoConvertToDDS = true;

    private static int counter = 14;
    private static void showAsTextureInFrame(BufferedImage bufferedImage) {
        if(counter-- < 0) { return; }
        JFrame frame = new JFrame("WINDOW");
        frame.setVisible(true);
        frame.add(new JLabel(new ImageIcon(bufferedImage)));
        frame.pack();
    }

    public static BufferedImage saveAsDDS(String path, BufferedImage bufferedImage) throws IOException {
        long start = System.currentTimeMillis();

        bufferedImage = rescaleToNextPowerOfTwo(bufferedImage);
        File ddsFile = new File(getFullPathAsDDS(path));
        if (ddsFile.exists()) {
            ddsFile.delete();
        }
        DDSUtilWriteLock.lock();
        try {
            DDSUtil.write(ddsFile, bufferedImage, DDSImage.D3DFMT_DXT5, true);
        } finally {
            DDSUtilWriteLock.unlock();
        }
        LOGGER.info("Converting and saving as dds took " + (System.currentTimeMillis() - start) + "ms");
        return bufferedImage;
    }

    public GlTextureTarget getTarget() {
        return target;
    }

    public void setTarget(GlTextureTarget target) {
        this.target = target;
    }

    public static BufferedImage rescaleToNextPowerOfTwo(BufferedImage nonPowerOfTwoImage) {
        int oldWidth = nonPowerOfTwoImage.getWidth();
        int nextPowerOfTwoWidth = getNextPowerOfTwo(oldWidth);
        int oldHeight = nonPowerOfTwoImage.getHeight();
        int nextPowerOfTwoHeight = getNextPowerOfTwo(oldHeight);
        BufferedImage result = nonPowerOfTwoImage;

        int maxOfWidthAndHeightPoT = Math.max(nextPowerOfTwoWidth, nextPowerOfTwoHeight);

        if(oldWidth != nextPowerOfTwoWidth || oldHeight != nextPowerOfTwoHeight) {
            result = new ImageRescaler().rescaleBI(nonPowerOfTwoImage, maxOfWidthAndHeightPoT, maxOfWidthAndHeightPoT);
            LOGGER.info("Image rescaled from " + oldWidth + " x " + oldHeight + " to " + result.getWidth() + " x " + result.getHeight());
        }
        return result;
    }

    @Override
    public String getName() {
        return path;
    }

    @Override
    public void load() {
        if(UPLOADING.equals(uploadState) || UPLOADED.equals(uploadState)) { return; }

        upload(textureManager, srgba);
    }

    @Override
    public void unload() {
        if(uploadState != UPLOADED || preventUnload) { return; }

        LOGGER.info("Unloading " + path);
        uploadState = NOT_UPLOADED;

        textureManager.getGpuContext().execute(() -> {
            ARBBindlessTexture.glMakeTextureHandleNonResidentARB(handle);
            LOGGER.info("Free VRAM: " + textureManager.getGpuContext().getAvailableVRAM());
        });
    }
    public long getHandle() {
        return handle;
    }

    private void bindWithoutReupload() {
        textureManager.getGpuContext().bindTexture(target, textureID);
    }

    public void setPreventUnload(boolean preventUnload) {
        this.preventUnload = preventUnload;
    }

    public enum UploadState {
        NOT_UPLOADED,
        UPLOADING,
        UPLOADED
    }
}
