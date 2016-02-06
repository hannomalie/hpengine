package texture;

import ddsutil.DDSUtil;
import ddsutil.ImageRescaler;
import engine.AppContext;
import event.TexturesChangedEvent;
import jogl.DDSImage;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import renderer.OpenGLContext;
import renderer.OpenGLThread;
import renderer.constants.GlTextureTarget;
import util.CompressionUtils;
import util.Util;
import util.ressources.Reloadable;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

import static renderer.constants.GlTextureTarget.TEXTURE_2D;
import static texture.Texture.DDSConversionState.*;
import static texture.Texture.UploadState.*;

public class Texture implements Serializable, Reloadable {
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
            lastUsedTimeStamp = System.currentTimeMillis();
            load();
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

    protected Texture() {
    }
	
    /**
     * Create a new texture
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
     * Bind the specified GL context to a texture
     *
     */
    public void bind() {
        if(textureID <= 0) {
//            System.out.println("texture id is <= 0");
            textureID = OpenGLContext.getInstance().genTextures();
        }
//        System.out.println("Binding texture with id " + textureID);
        OpenGLContext.getInstance().bindTexture(target, textureID);
    }
    public void bind(int unit) {
        if(textureID <= 0) {
//            System.out.println("texture id is <= 0");
            textureID = OpenGLContext.getInstance().genTextures();
        }
//        System.out.println("Binding texture with id " + textureID);
        OpenGLContext.getInstance().bindTexture(unit, target, textureID);
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


    public void setData(byte[] data) {
        setData(0, data);
    }
    public void setData(int i, byte[] data) {
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
//		imageBuffer.order(ByteOrder.nativeOrder());
		imageBuffer.put(data[0], 0, data[0].length);
		imageBuffer.flip();
		return imageBuffer;
	}

    public void upload() {
        upload(false);
    }
	public void upload(boolean srgba) {
        this.srgba = srgba;
        upload(buffer(), srgba);
	}
    Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread th, Throwable ex) {
            System.out.println("Uncaught exception: " + ex);
        }
    };
	public void upload(ByteBuffer textureBuffer) {
		upload(textureBuffer, false);
	}
	
	public void upload(ByteBuffer textureBuffer, boolean srgba) {
        if(UPLOADING.equals(uploadState) || UPLOADED.equals(uploadState)) { return; }
        uploadState = UPLOADING;
        new OpenGLThread() {
            @Override
            public void doRun() {
                setUncaughtExceptionHandler(uncaughtExceptionHandler);
                OpenGLContext.getInstance().execute(() -> {
                    System.out.println("Uploading " + path);
                    bind(0);
                    if (target == TEXTURE_2D)
                    {
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0);
                        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, Util.calculateMipMapCount(Math.max(width,height)));
                    }
                    int internalformat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
                    //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
                    if(srgba) {
                        internalformat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
                        //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
                    }
                    synchronized (data) {
                        if(sourceDataCompressed) {
                            System.out.println("#########Loading compressed texture");
                            GL13.glCompressedTexImage2D(target.glTarget,
                                    0,
                                    internalformat,
                                    getWidth(),
                                    getHeight(),
                                    0,
                                    textureBuffer);
                            OpenGLContext.exitOnGLError("ZZZ");
                        } else {
                            GL11.glTexImage2D(target.glTarget,
                                    0,
                                    internalformat,
                                    getWidth(),
                                    getHeight(),
                                    0,
                                    srcPixelFormat,
                                    GL11.GL_UNSIGNED_BYTE,
                                    textureBuffer);
                        }
                    }
                if(mipmapsGenerated)
                {
                    final int finalInternalformat = internalformat;
                    OpenGLContext.getInstance().execute(() -> {
                        bind();
                        uploadMipMaps(finalInternalformat);
                    });
                } else {
                    OpenGLContext.getInstance().execute(() -> {
                        bind();
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                    });
                }
                uploadState = UPLOADED;
                System.out.println("Upload finished");
                AppContext.getEventBus().post(new TexturesChangedEvent());
            }, false);
            }
        }.start();

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
        System.out.println("Uploading mipmaps for " + path);
        int currentWidth = width/2;
        int currentHeight = height/2;
        for(int i = 1; i < mipmapCount; i++) {
//            System.out.println("Uploading mipmap " + i + " with " + currentWidth + " x " + currentHeight);
            ByteBuffer tempBuffer = BufferUtils.createByteBuffer(((currentWidth + 3) / 4) * ((currentHeight + 3) / 4) * 16);//currentHeight * currentWidth * 4);
            tempBuffer.rewind();
            tempBuffer.put(data[i]);
            tempBuffer.rewind();
//            System.out.println("Mipmap buffering with " + tempBuffer.remaining() + " remaining bytes for " + currentWidth + " x " +  currentHeight);
            if(sourceDataCompressed) {
                GL13.glCompressedTexImage2D(target.glTarget,
                        i,
                        internalformat,
                        currentWidth,
                        currentHeight,
                        0,
                        tempBuffer);
                OpenGLContext.exitOnGLError("XXX");
            } else {
                GL11.glTexImage2D(target.glTarget,
                        i,
                        internalformat,
                        currentWidth,
                        currentHeight,
                        0,
                        srcPixelFormat,
                        GL11.GL_UNSIGNED_BYTE,
                        tempBuffer);
                OpenGLContext.exitOnGLError("YYY");
            }
            int minSize = 1;
            currentWidth = Math.max(minSize, currentWidth/2);
            currentHeight = Math.max(minSize, currentHeight/2);
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
//		System.out.println("TEXTURE READ IN " +  (System.currentTimeMillis() - start) + " ms");
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
//		System.out.println("Compression took " + (System.currentTimeMillis() - start));
	}

	protected void decompress() throws IOException {
		try {
//	    	long start = System.currentTimeMillis();
			synchronized (data) {
				setData(CompressionUtils.decompress(getData()));
			}
//			System.out.println("Decompression took " + (System.currentTimeMillis() - start));
		} catch (DataFormatException e) {
			e.printStackTrace();
		}
	}
	
	public static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/textures/";
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
                System.out.println("Thread started ...");
                FileInputStream fis = new FileInputStream(getDirectory() + FilenameUtils.getBaseName(path) + ".hptexture");
                ObjectInputStream in = new ObjectInputStream(fis);
                Texture texture = (Texture) in.readObject();
                in.close();
                init(texture);
                System.out.println("Data: " + getData().length);
                upload();
                System.out.println("Uploaded");
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

    private ExecutorService multiThreadService = Executors.newFixedThreadPool(4);
    private static ReentrantLock DDSUtilWriteLock = new ReentrantLock();
    private static final boolean autoConvertToDDS = true;

    public void convertAndUpload() {
        multiThreadService.submit(() -> {
            try {
                data = new byte[1][];

                boolean imageExists = new File(path).exists();
                boolean ddsRequested = FilenameUtils.isExtension(path, "dds");
                BufferedImage bufferedImage;

                long start = System.currentTimeMillis();
                if(imageExists) {
                    System.out.println(path + " available as dds: " + textureAvailableAsDDS(path));
                    if(!textureAvailableAsDDS(path)) {
                        bufferedImage = TextureFactory.getInstance().loadImage(path);
                        if (bufferedImage != null) {
                            data = new byte[util.Util.calculateMipMapCount(Math.max(bufferedImage.getWidth(), bufferedImage.getHeight()))+1][];
                        }
                        if(autoConvertToDDS) {
                            new Thread(() -> {
                                try {
                                    saveAsDDS(bufferedImage);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }

                    } else { // texture available as dds
                        ddsConversionState = CONVERTED;
                        DDSImage ddsImage = DDSImage.read(new File(getFullPathAsDDS(path)));
                        int mipMapCountPlusOne = Util.calculateMipMapCountPlusOne(ddsImage.getWidth(), ddsImage.getHeight());
                        mipmapCount = mipMapCountPlusOne -1;
                        data = new byte[mipMapCountPlusOne][];
                        for(int i = 0; i < ddsImage.getAllMipMaps().length; i++) {
                            DDSImage.ImageInfo info = ddsImage.getMipMap(i);

//                        BufferedImage mipmapimage = DDSUtil.decompressTexture(info.getData(), info.getWidth(), info.getHeight(), info.getCompressionFormat());
//                        showAsTextureInFrame(mipmapImage);
//                        data[i] = TextureFactory.getInstance().convertImageData(mipmapImage);
                            data[i] = new byte[info.getData().capacity()];
                            info.getData().get(data[i]);
                        }
                        setWidth(ddsImage.getWidth());
                        setHeight(ddsImage.getHeight());
                        if(ddsImage.getNumMipMaps() > 1) {
                            mipmapsGenerated = true;
                        }
                        sourceDataCompressed = true;
                        upload(buffer());
                        System.out.println("" + (System.currentTimeMillis() - start) + "ms for loading and uploading as dds with mipmaps: " + path);
                        return;
                    }

                } else {
                    bufferedImage = TextureFactory.getInstance().getDefaultTextureAsBufferedImage();
                    System.out.println("Texture cannot be read, default texture data inserted instead...");
                }

                setWidth(bufferedImage.getWidth());
                setHeight(bufferedImage.getHeight());
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
                System.out.println("" + (System.currentTimeMillis() - start) + "ms for loading and uploading without mipmaps: " + path);
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
                Logger.getGlobal().severe("Texture not found: " + path + ". Default texture returned...");
            }
        });
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
        System.out.println("Converting and saving as dds took " + (System.currentTimeMillis() - start) + "ms");
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
            System.out.println("Image rescaled from " + oldWidth + " x " + oldHeight + " to " + result.getWidth() + " x " + result.getHeight());
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
        System.out.println("Loading " + path);

//        upload(srgba);
    }

    @Override
    public void unload() {
        if(uploadState != UPLOADED || preventUnload) { return; }

        System.out.println("Unloading " + path);
        uploadState = NOT_UPLOADED;
//        System.out.println("Free VRAM: " + OpenGLContext.getInstance().getAvailableVRAM());
//        System.out.println("Total: " + OpenGLContext.getInstance().getAvailableTotalVRAM());
//        System.out.println("Dedicated: " + OpenGLContext.getInstance().getDedicatedVRAM());
    }
}