package de.hanno.hpengine.texture;

import ddsutil.DDSUtil;
import ddsutil.ImageRescaler;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.event.TexturesChangedEvent;
import jogl.DDSImage;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.util.CompressionUtils;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.ressources.Reloadable;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

import static org.lwjgl.opengl.GL15.GL_STREAM_COPY;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static de.hanno.hpengine.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.texture.Texture.DDSConversionState.*;
import static de.hanno.hpengine.texture.Texture.UploadState.*;

public class Texture implements Serializable, Reloadable {

    private static final Logger LOGGER = Logger.getLogger(Texture.class.getName());

	private static final long serialVersionUID = 1L;
    public static final boolean COMPILED_TEXTURES = false;
    private transient boolean srgba;

    private String path = "";

    private volatile boolean mipmapsGenerated = false;
    private boolean sourceDataCompressed = false;

    private transient long lastUsedTimeStamp = System.currentTimeMillis();
    private transient volatile boolean preventUnload = false;

    public void setUsedNow() {
        if(NOT_UPLOADED.equals(uploadState)) {
            load();
        } else if(UPLOADED.equals(uploadState) || UPLOADING.equals(uploadState)) {
            lastUsedTimeStamp = System.currentTimeMillis();
        }
    }

    public DDSConversionState getDdsConversionState() {
        return ddsConversionState;
    }

    public long getLastUsedTimeStamp() {
        return lastUsedTimeStamp;
    }

    public UploadState getUploadState() {
        return uploadState;
    }

    public void setPreventUnload(boolean preventUnload) {
        this.preventUnload = preventUnload;
    }


    public enum UploadState {
        NOT_UPLOADED,
        UPLOADING,
        UPLOADED
    }
    private transient volatile UploadState uploadState = NOT_UPLOADED;

    public enum DDSConversionState {
        NOT_CONVERTED,
        CONVERTING,
        CONVERTED
    }
    private DDSConversionState ddsConversionState = NOT_CONVERTED;
	protected GlTextureTarget target = TEXTURE_2D;
    transient protected int textureID = -1;
    protected int height;
    protected int width;

    protected volatile byte[][] data;
    protected int dstPixelFormat = GL11.GL_RGBA;
    protected int srcPixelFormat = GL11.GL_RGBA;
    protected int minFilter = GL11.GL_LINEAR;
    protected int magFilter = GL11.GL_LINEAR;
    private int mipmapCount = -1;
    protected long handle =-1L;


    protected Texture() {
        genHandle();
    }

    private void genHandle() {
        OpenGLContext.getInstance().execute(() -> {
            if(handle <= 0) {
                bind(15);
                handle =  ARBBindlessTexture.glGetTextureHandleARB(textureID);
                unbind(15);
            }
        });
    }

    /**
     * Create a new de.hanno.hpengine.texture
     */
    public Texture(String path) {
        this(path, false);
    }

    public Texture(String path, boolean srgba) {
        this(path, TEXTURE_2D, srgba);
    }

    public Texture(String path, GlTextureTarget target, boolean srgba) {
        this.target = target;
        this.path = path;
        this.srgba = srgba;
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
            textureID = OpenGLContext.getInstance().genTextures();
        }
        if(setUsed) {
            setUsedNow();
        }
        OpenGLContext.getInstance().bindTexture(target, textureID);
    }

    public void bind(int unit) {
        bind(unit, true);
    }
    public void bind(int unit, boolean setUsed) {
        if(textureID <= 0) {
            textureID = OpenGLContext.getInstance().genTextures();
        }
        if(setUsed) {
            setUsedNow();
        }
        OpenGLContext.getInstance().bindTexture(unit, target, textureID);
    }
    public void  unbind(int unit) {
        OpenGLContext.getInstance().bindTexture(unit, target, 0);
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


    private void setData(byte[] data) {
        setData(0, data);
    }
    private void setData(int i, byte[] data) {
        this.data[i] = data;
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

    public void upload() {
        upload(false);
    }
	public void upload(boolean srgba) {
        this.srgba = srgba;
        if(data == null || data[0] == null) { return; }
        upload(buffer(), srgba);
	}
    Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread th, Throwable ex) {
            LOGGER.severe("Uncaught exception: " + ex);
            ex.printStackTrace();
        }
    };

	public void upload(ByteBuffer textureBuffer) {
		upload(textureBuffer, false);
	}
	
	public void upload(ByteBuffer textureBuffer, boolean srgba) {
        if(UPLOADING.equals(uploadState) || UPLOADED.equals(uploadState)) { return; }
        uploadState = UPLOADING;

        Runnable uploadRunnable = () -> {
            LOGGER.info("Uploading " + path);
            int internalformat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
            if (srgba) {
                internalformat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
            }
            int finalInternalformat = internalformat;

            OpenGLContext.getInstance().execute(() -> {
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
            OpenGLContext.getInstance().execute(() -> {
                if(!mipmapsGenerated) {
                    bind(15);
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                    unbind(15);

                }
            });
            genHandle();
            OpenGLContext.getInstance().execute(() -> {
                ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
            });
            setUploaded();
            Engine.getEventBus().post(new TexturesChangedEvent());
        };

        TextureFactory.getInstance().getCommandQueue().addCommand(uploadRunnable);
    }

    private void setUploaded() {
        uploadState = UPLOADED;
        LOGGER.info("Upload finished");
        LOGGER.fine("Free VRAM: " + OpenGLContext.getInstance().getAvailableVRAM());
    }

    private void uploadWithPixelBuffer(ByteBuffer textureBuffer, int internalformat, int width, int height, int mipLevel, boolean sourceDataCompressed, boolean setMaxLevel) {
        textureBuffer.rewind();
        final AtomicInteger pbo = new AtomicInteger(-1);
        ByteBuffer temp = OpenGLContext.getInstance().calculate(() -> {
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
        OpenGLContext.getInstance().execute(() -> {
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

    private void downloadMipMaps() {
        int currentWidth = width/2;
        int currentHeight = height/2;
        for(int i = 1; i < mipmapCount; i++) {
            ByteBuffer tempBuffer = BufferUtils.createByteBuffer(currentHeight * currentWidth * 4);
            tempBuffer.rewind();
            TextureFactory.getTextureData(textureID, i, GL11.GL_RGBA, tempBuffer);
            tempBuffer.get(data[i]);
            currentWidth /= 2;
            currentHeight /= 2;
        }
        mipmapsGenerated = true;
    }

    private void uploadMipMaps(int internalformat) {
        LOGGER.info("Uploading mipmaps for " + path);
        int currentWidth = width;
        int currentHeight = height;
        List<Integer> widths = new ArrayList();
        List<Integer> heights = new ArrayList();
        for(int i = 0; i < mipmapCount-1; i++) {
            int minSize = 1;
            currentWidth = Math.max(minSize, currentWidth/2);
            currentHeight = Math.max(minSize, currentHeight/2);
            widths.add(currentWidth);
            heights.add(currentHeight);
        }
        for(int i = mipmapCount-1; i >= 1; i--) {
            currentWidth = widths.get(i - 1);
            currentHeight = heights.get(i - 1);
            int mipmapIndex = i;
            ByteBuffer tempBuffer = BufferUtils.createByteBuffer(data[mipmapIndex].length);//currentHeight * currentWidth * 4);
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

    public static Texture read(String resourceName, int textureId) {
        return read(resourceName, textureId, false);
    }
    public static Texture read(String resourceName, int textureId, boolean srgba) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;

		try {
			fis = new FileInputStream(getDirectory() + fileName + ".hptexture");
			in = new ObjectInputStream(fis);
			Texture texture = (Texture) in.readObject();
			in.close();
			texture.textureID = textureId;
			texture.upload(srgba);
			return texture;
		} catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static boolean write(Texture texture, String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hptexture");
			out = new ObjectOutputStream(fos);
			out.writeObject(texture);
			return true;

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
//		long start = System.currentTimeMillis();
		in.defaultReadObject();
	    decompress();
//		LOGGER.info("TEXTURE READ IN " +  (System.currentTimeMillis() - start) + " ms");
	}

	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		compress();
        oos.defaultWriteObject();
    }

	protected void compress() throws IOException {
//    	long start = System.currentTimeMillis();
		synchronized (data) {
			setData(CompressionUtils.compress(getData()));
		}
//		LOGGER.info("Compression took " + (System.currentTimeMillis() - start));
	}

	protected void decompress() throws IOException {
		try {
//	    	long start = System.currentTimeMillis();
			synchronized (data) {
				setData(CompressionUtils.decompress(getData()));
			}
//			LOGGER.info("Decompression took " + (System.currentTimeMillis() - start));
		} catch (DataFormatException e) {
			e.printStackTrace();
		}
	}
	
	public static String getDirectory() {
		return Engine.WORKDIR_NAME + "/assets/textures/";
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

	public void setTextureID(int textureID) {
		this.textureID = textureID;
	}

	public static boolean filterRequiresMipmaps(int magTextureFilter) {
		return (magTextureFilter == GL11.GL_LINEAR_MIPMAP_LINEAR ||
				magTextureFilter == GL11.GL_LINEAR_MIPMAP_NEAREST ||
				magTextureFilter == GL11.GL_NEAREST_MIPMAP_LINEAR ||
				magTextureFilter == GL11.GL_NEAREST_MIPMAP_NEAREST);
	}

    public void readAndUpload() {
        new Thread(() -> {
            try {
                LOGGER.info("Thread started ...");
                FileInputStream fis = new FileInputStream(getDirectory() + FilenameUtils.getBaseName(path) + ".hptexture");
                ObjectInputStream in = new ObjectInputStream(fis);
                Texture texture = (Texture) in.readObject();
                in.close();
                init(texture);
                LOGGER.fine("Data: " + getData().length);
                upload();
            } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void init(Texture texture) {
        setData(texture.getData());
        setWidth(texture.getWidth());
        setHeight(texture.getHeight());
        setTarget(texture.getTarget());
        setDstPixelFormat(texture.getDstPixelFormat());
        setSrcPixelFormat(texture.getSrcPixelFormat());
        setMinFilter(texture.getMinFilter());
        setMagFilter(texture.getMagFilter());
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
        String fullDDSPath = restPath + name + ".dds";
        return fullDDSPath;
    }
    protected static int getNextPowerOfTwo(int fold) {
        int ret = 2;
        while (ret < fold) {
            ret *= 2;
        }
        return ret;
    }

    private static ReentrantLock DDSUtilWriteLock = new ReentrantLock();
    private static final boolean autoConvertToDDS = true;

    public void convertAndUpload() {
        CompletableFuture<Object> future = TextureFactory.getInstance().getCommandQueue().addCommand(() -> {
            try {
                LOGGER.severe(path);
                data = new byte[1][];

                boolean imageExists = new File(path).exists();
                boolean ddsRequested = FilenameUtils.isExtension(path, "dds");
                BufferedImage bufferedImage;

                long start = System.currentTimeMillis();
                if (imageExists) {
                    LOGGER.info(path + " available as dds: " + textureAvailableAsDDS(path));
                    if (!textureAvailableAsDDS(path)) {
                        bufferedImage = TextureFactory.getInstance().loadImage(path);
                        if (bufferedImage != null) {
                            data = new byte[Util.calculateMipMapCount(Math.max(bufferedImage.getWidth(), bufferedImage.getHeight())) + 1][];
                        }
                        if (autoConvertToDDS) {
                            new Thread(() -> {
                                try {
                                    saveAsDDS(bufferedImage);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }

                    } else { // de.hanno.hpengine.texture available as dds
                        ddsConversionState = CONVERTED;
                        DDSImage ddsImage = DDSImage.read(new File(getFullPathAsDDS(path)));
                        int mipMapCountPlusOne = Util.calculateMipMapCountPlusOne(ddsImage.getWidth(), ddsImage.getHeight());
                        mipmapCount = mipMapCountPlusOne - 1;
                        data = new byte[mipMapCountPlusOne][];
                        for (int i = 0; i < ddsImage.getAllMipMaps().length; i++) {
                            DDSImage.ImageInfo info = ddsImage.getMipMap(i);

//                        BufferedImage mipmapimage = DDSUtil.decompressTexture(info.getData(), info.getWidth(), info.getHeight(), info.getCompressionFormat());
//                        showAsTextureInFrame(mipmapImage);
//                        data[i] = TextureFactory.getInstance().convertImageData(mipmapImage);
                            data[i] = new byte[info.getData().capacity()];
                            info.getData().get(data[i]);
                        }
                        setWidth(ddsImage.getWidth());
                        setHeight(ddsImage.getHeight());
                        if (ddsImage.getNumMipMaps() > 1) {
                            mipmapsGenerated = true;
                        }
                        sourceDataCompressed = true;
                        upload(buffer(), this.srgba);
                        LOGGER.info("" + (System.currentTimeMillis() - start) + "ms for loading and uploading as dds with mipmaps: " + path);
                        return;
                    }

                } else {
                    bufferedImage = TextureFactory.getInstance().getDefaultTextureAsBufferedImage();
                    LOGGER.severe("Texture " + path + " cannot be read, default de.hanno.hpengine.texture data inserted instead...");
                }

                setWidth(bufferedImage.getWidth());
                setHeight(bufferedImage.getHeight());
                int mipMapCountPlusOne = Util.calculateMipMapCountPlusOne(getWidth(), getHeight());
                mipmapCount = mipMapCountPlusOne - 1;
                setMinFilter(minFilter);
                setMagFilter(magFilter);

                if (bufferedImage.getColorModel().hasAlpha()) {
                    srcPixelFormat = GL11.GL_RGBA;
                } else {
                    srcPixelFormat = GL11.GL_RGB;
                }

                setDstPixelFormat(dstPixelFormat);
                setSrcPixelFormat(srcPixelFormat);

                byte[] bytes = TextureFactory.getInstance().convertImageData(bufferedImage);
                setData(bytes);
                ByteBuffer textureBuffer = buffer();
                upload(textureBuffer, srgba);
                LOGGER.info("" + (System.currentTimeMillis() - start) + "ms for loading and uploading without mipmaps: " + path);
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
                LOGGER.severe("Texture not found: " + path + ". Default de.hanno.hpengine.texture returned...");
            }
            Engine.getEventBus().post(new TexturesChangedEvent());
        });
        try {
            // TODO: Check out why this is necessary
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private static int counter = 14;
    private static void showAsTextureInFrame(BufferedImage bufferedImage) {
        if(counter-- < 0) { return; }
        JFrame frame = new JFrame("WINDOW");
        frame.setVisible(true);
        frame.add(new JLabel(new ImageIcon(bufferedImage)));
        frame.pack();
    }

    private BufferedImage saveAsDDS(BufferedImage bufferedImage) throws IOException {
        long start = System.currentTimeMillis();
        ddsConversionState = CONVERTING;

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
        ddsConversionState = CONVERTED;
        LOGGER.info("Converting and saving as dds took " + (System.currentTimeMillis() - start) + "ms");
        return bufferedImage;
    }

    public GlTextureTarget getTarget() {
        return target;
    }

    public void setTarget(GlTextureTarget target) {
        this.target = target;
    }

    public int getDstPixelFormat() {
        return dstPixelFormat;
    }

    public int getSrcPixelFormat() {
        return srcPixelFormat;
    }

    public int getMinFilter() {
        return minFilter;
    }

    public int getMagFilter() {
        return magFilter;
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
//        LOGGER.info("Loading " + path);

        upload(srgba);
    }

    @Override
    public void unload() {
        if(uploadState != UPLOADED || preventUnload) { return; }

        LOGGER.info("Unloading " + path);
        uploadState = NOT_UPLOADED;

        OpenGLContext.getInstance().execute(() -> {
            ARBBindlessTexture.glMakeTextureHandleNonResidentARB(handle);
            LOGGER.info("Free VRAM: " + OpenGLContext.getInstance().getAvailableVRAM());
        });
    }
    public long getHandle() {
        return handle;
    }

    private void bindWithoutReupload() {
        OpenGLContext.getInstance().bindTexture(target, textureID);
    }
}