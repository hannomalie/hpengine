package texture;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import engine.AppContext;
import org.lwjgl.opengl.*;
import renderer.DeferredRenderer;
import renderer.Renderer;
import renderer.command.Command;
import renderer.command.Result;
import renderer.constants.GlTextureTarget;
import renderer.material.Material;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.lwjgl.util.vector.Vector2f;

import static renderer.constants.GlTextureTarget.TEXTURE_2D;
import static renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP;
import static renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY;

/**
 * A utility class to load textures for JOGL. This source is based
 * on a texture that can be found in the Java Gaming (www.javagaming.org)
 * Wiki. It has been simplified slightly for explicit 2D graphics use.
 * 
 * OpenGL uses a particular image format. Since the images that are 
 * loaded from disk may not match this format this loader introduces
 * a intermediate image which the source image is copied into. In turn,
 * this image is used as source for the OpenGL texture.
 *
 * @author Kevin Glass
 * @author Brian Matzon
 */
public class TextureFactory {
	private Renderer renderer;
    /** The table of textures that have been loaded in this loader */
    public Map<String, Texture> TEXTURES = new ConcurrentHashMap<String, Texture>();

    /** The colour model including alpha for the GL image */
    private ColorModel glAlphaColorModel;
    
    /** The colour model for the GL image */
    private ColorModel glColorModel;
    
    /** 
     * Create a new texture loader based on the game panel
     *
     */
    Texture defaultTexture = null;

    public TextureFactory(Renderer renderer) {
        DeferredRenderer.exitOnGLError("Begin TextureFactory constructor");
    	this.renderer = renderer;
        glAlphaColorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                            new int[] {8,8,8,8},
                                            true,
                                            false,
                                            ComponentColorModel.TRANSLUCENT,
                                            DataBuffer.TYPE_BYTE);
                                            
        glColorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                            new int[] {8,8,8,0},
                                            false,
                                            false,
                                            ComponentColorModel.OPAQUE,
                                            DataBuffer.TYPE_BYTE);

//    	loadAllAvailableTextures();

        defaultTexture = getTexture("hp/assets/models/textures/gi_flag.png", true);
        DeferredRenderer.exitOnGLError("After TextureFactory constructor");
    }
    
    private void loadAllAvailableTextures() {
    	File textureDir = new File(Texture.getDirectory());
    	List<File> files = (List<File>) FileUtils.listFiles(textureDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
		DeferredRenderer.exitOnGLError("Before loadAllAvailableTextures");
		for (File file : files) {
			try {
				if(FilenameUtils.isExtension(file.getAbsolutePath(), "hptexture")) {
					getTexture(file.getAbsolutePath());
				} else {
					getCubeMap(file.getAbsolutePath());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    }

    public boolean removeTexture(String path) {
        if(TEXTURES.containsKey(path)) {
            TEXTURES.remove(path);
            return !TEXTURES.containsKey(path);
        }
        return true;
    }

    public Texture getDefaultTexture() {
        return defaultTexture;
    }

    /**
     * Create a new texture ID 
     *
     * @return A new texture ID
     */
    private int createTextureID()
    {
        return renderer.getOpenGLContext().calculateWithOpenGLContext(() -> {
                    return getTextureId();
                }
        );
    }
    
    /**
     * Load a texture
     *
     * @param resourceName The location of the resource to load
     * @return The loaded texture
     * @throws IOException Indicates a failure to access the resource
     */
    public Texture getTexture(String resourceName) {
    	return getTexture(resourceName, false);
    }
    public Texture getTexture(String resourceName, boolean srgba) {
        if(textureLoaded(resourceName)) {
            return TEXTURES.get(resourceName);
        }

        if (texturePreCompiled(resourceName)) {
            Texture texture = new Texture(resourceName, getTextureId(), srgba);
            TEXTURES.put(resourceName, texture);
            texture.readAndUpload();
            return texture;
        }

        Texture texture = new Texture(resourceName, getTextureId(), srgba);
        TEXTURES.put(resourceName, texture);
        texture.convertAndUpload();
        return texture;
    }

    public static int getTextureId() {
        return AppContext.getInstance().getRenderer().getOpenGLContext().calculateWithOpenGLContext(() -> GL11.glGenTextures());
    }

    private boolean textureLoaded(String resourceName) {
        return TEXTURES.containsKey(resourceName);
    }

    public boolean texturePreCompiled(String resourceName) {
    	String fileName = FilenameUtils.getBaseName(resourceName);
    	File f = new File(Texture.getDirectory() + fileName + ".hptexture");
    	return f.exists();
	}
    

	private boolean cubeMapPreCompiled(String resourceName) {
    	String fileName = FilenameUtils.getBaseName(resourceName);
    	File f = new File(Texture.getDirectory() + fileName + ".hpcubemap");
    	return f.exists();
	}

	public CubeMap getCubeMap(String resourceName) throws IOException {
		CubeMap tex = (CubeMap) TEXTURES.get(resourceName+ "_cube");
        
        if (tex != null && tex instanceof CubeMap) {
            return tex;
        }

        if (cubeMapPreCompiled(resourceName)) {
        	tex = CubeMap.read(resourceName, createTextureID());
        	if (tex != null) {
                generateMipMaps(tex, Material.MIPMAP_DEFAULT);
                TEXTURES.put(resourceName+ "_cube",tex);
                return tex;
            }
        }
        
        tex = getCubeMap(resourceName,
                         GL11.GL_RGBA,     // dst pixel format
                         GL11.GL_LINEAR_MIPMAP_LINEAR, // min filter (unused)
                         GL11.GL_LINEAR, false);

        TEXTURES.put(resourceName + "_cube",tex);
        System.out.println("Precompiled " + CubeMap.write(tex, resourceName));
        return tex;
    }

    private CubeMap getCubeMap(String resourceName, 
					            int dstPixelFormat,
					            int minFilter, 
					            int magFilter, boolean asStream) throws IOException {

        GlTextureTarget target = TEXTURE_CUBE_MAP;

    	 int srcPixelFormat = 0;
         
         // create the texture ID for this texture 
         int textureID = createTextureID(); 
         CubeMap cubeMap = new CubeMap(resourceName, target, textureID); 
         
         // bind this texture
        renderer.getOpenGLContext().bindTexture(target, textureID);

         BufferedImage bufferedImage = null;
         if (asStream) {
             bufferedImage = loadImageAsStream(resourceName);
         } else {
             bufferedImage = loadImage(resourceName);
         } 
         cubeMap.setWidth(bufferedImage.getWidth());
         cubeMap.setHeight(bufferedImage.getHeight());
         cubeMap.setMinFilter(minFilter);
         cubeMap.setMagFilter(magFilter);
         
         if (bufferedImage.getColorModel().hasAlpha()) {
             srcPixelFormat = GL11.GL_RGBA;
         } else {
             srcPixelFormat = GL11.GL_RGB;
         }

         cubeMap.setDstPixelFormat(dstPixelFormat);
         cubeMap.setSrcPixelFormat(srcPixelFormat);
         
         // convert that image into a byte buffer of texture data 
         ByteBuffer[] textureBuffers = convertCubeMapData(bufferedImage,cubeMap);
         
         cubeMap.upload();
         
         return cubeMap; 
	}
    
    /**
     * Convert the buffered image to a texture
     *
     * @param bufferedImage The image to convert to a texture
     * @param texture The texture to store the data into
     * @return A buffer containing the data
     */
    public ByteBuffer convertImageData(BufferedImage bufferedImage, Texture texture) {
        ByteBuffer imageBuffer = null; 
        WritableRaster raster;
        BufferedImage texImage;

        int width = bufferedImage.getWidth();
        texture.setWidth(width);
        int height = bufferedImage.getHeight();
        texture.setHeight(height);
        
        // create a raster that can be used by OpenGL as a source
        // for a texture
        if (bufferedImage.getColorModel().hasAlpha()) {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,width,height,4,null);
            texImage = new BufferedImage(glAlphaColorModel,raster,false,new Hashtable());
        } else {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,width,height,3,null);
            texImage = new BufferedImage(glColorModel,raster,false,new Hashtable());
        }
            
        // copy the source image into the produced image
        Graphics g = texImage.getGraphics();
        g.setColor(new Color(0f,0f,0f,0f));
        g.fillRect(0,0,width,height);
        g.drawImage(bufferedImage,0,0,null);
        
        // build a byte buffer from the temporary image 
        // that be used by OpenGL to produce a texture.
        byte[] data = ((DataBufferByte) texImage.getRaster().getDataBuffer()).getData(); 

        texture.setData(data);
        
        return texture.buffer();
    }
    
    private ByteBuffer[] convertCubeMapData(BufferedImage bufferedImage,CubeMap cubeMap) { 
        ByteBuffer imageBuffers[] = new ByteBuffer[6];
        List<byte[]> byteArrays = new ArrayList<>();
        
        WritableRaster raster;
        BufferedImage texImage;


        int width = cubeMap.getWidth();
        int height = cubeMap.getHeight();

        int tileWidth = (width /4);
        int tileHeight = (height /3);
    	
        for(int i = 0; i < 6; i++) {

        	Vector2f[] topLeftBottomRight = getRectForFaceIndex(i, bufferedImage.getWidth(), bufferedImage.getHeight());
            
            if (bufferedImage.getColorModel().hasAlpha()) {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,tileWidth,tileHeight,4,null);
                texImage = new BufferedImage(glAlphaColorModel,raster,false,new Hashtable());
            } else {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,tileWidth,tileHeight,3,null);
                texImage = new BufferedImage(glColorModel,raster,false,new Hashtable());
            }
            
            Graphics g = texImage.getGraphics();
            g.setColor(new Color(0f,0f,0f,0f));
            g.fillRect(0,0,tileWidth,tileHeight);
            
            g.drawImage(bufferedImage,0,0, tileWidth, tileHeight, (int)topLeftBottomRight[0].x,(int)topLeftBottomRight[0].y,
					  (int)topLeftBottomRight[1].x,(int)topLeftBottomRight[1].y, null);

//            try {
//                File outputfile = new File(i + ".png");
//                ImageIO.write(texImage, "png", outputfile);
//            } catch (IOException e) {
//            	System.out.println("xoxoxoxo");
//            }
            
            
            byte[] data = ((DataBufferByte) texImage.getRaster().getDataBuffer()).getData(); 
            byteArrays.add(data);
            
    		ByteBuffer tempBuffer = ByteBuffer.allocateDirect(data.length);
    		tempBuffer.order(ByteOrder.nativeOrder());
    		tempBuffer.put(data, 0, data.length);
    		tempBuffer.flip();
            
            imageBuffers[i] = tempBuffer;
            
		}
//        System.exit(0);
        cubeMap.setData(byteArrays);
        return imageBuffers;
    }
    
    private Vector2f[] getRectForFaceIndex(int index, int imageWidth, int imageHeight) {
    	Vector2f[] result = new Vector2f[2];
    	
    	switch (index) {
		case 0: // GL_TEXTURE_CUBE_MAP_POSITIVE_X
			result[0] = new Vector2f(imageWidth/2, imageHeight/3);
			result[1] = new Vector2f(3*imageWidth/4, 2*imageHeight/3);
			break;

		case 1: // GL_TEXTURE_CUBE_MAP_NEGATIVE_X
			result[0] = new Vector2f(0, imageHeight/3);
			result[1] = new Vector2f(imageWidth/4, 2*imageHeight/3);
			break;

		case 2: // GL_TEXTURE_CUBE_MAP_POSITIVE_Y
			result[1] = new Vector2f(imageWidth/4, 0);
			result[0] = new Vector2f(imageWidth/2, imageHeight/3);
			break;

		case 3: // GL_TEXTURE_CUBE_MAP_NEGATIVE_Y
			result[0] = new Vector2f(imageWidth/4, 2*imageHeight/3);
			result[1] = new Vector2f(imageWidth/2, imageHeight);
			break;

		case 4: // GL_TEXTURE_CUBE_MAP_POSITIVE_Z
			result[0] = new Vector2f(3*imageWidth/4, imageHeight/3);
			result[1] = new Vector2f(imageWidth, 2*imageHeight/3);
			break;

		case 5: // GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
			result[0] = new Vector2f(imageWidth/4, imageHeight/3);
			result[1] = new Vector2f(imageWidth/2, 2*imageHeight/3);
			break;

		default:
			break;
		}
    	
    	return result;
	}

	/** 
     * Load a given resource as a buffered image
     * 
     * @param ref The location of the resource to load
     * @return The loaded buffered image
     * @throws IOException Indicates a failure to find a resource
     */
    public BufferedImage loadImage(String ref) throws IOException
    {
        URL url = TextureFactory.class.getClassLoader().getResource(ref);
        
        if (url == null) {
//            throw new IOException("Cannot find: "+ref);
            return loadImageAsStream(ref);
        }
        
        BufferedImage bufferedImage = ImageIO.read(new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(ref)));

        return bufferedImage;
    }

    public BufferedImage loadImageAsStream(String ref) throws IOException
    { 
        BufferedImage bufferedImage = null;
		try {
			bufferedImage = ImageIO.read(new File(ref));
		} catch (Exception e) {
			System.err.println("Unable to read file " + ref);
		} 
 
        return bufferedImage;
    }
    
    /**
     * Creates an integer buffer to hold specified ints
     * - strictly a utility method
     *
     * @param size how many int to contain
     * @return created IntBuffer
     */
    protected IntBuffer createIntBuffer(int size) {
      ByteBuffer temp = ByteBuffer.allocateDirect(4 * size);
      temp.order(ByteOrder.nativeOrder());

      return temp.asIntBuffer();
    }

    private void generateMipMaps(Texture texture, boolean mipmap) {
        texture.bind();
    	if (mipmap) {
    		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
    		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    	}

    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
    }
    public void generateMipMaps(int textureId) {
        generateMipMaps(textureId, GL11.GL_LINEAR_MIPMAP_LINEAR);
    }
    public void generateMipMaps(int textureId, int textureMinFilter) {
        generateMipMaps(textureId, textureMinFilter, GL11.GL_LINEAR);
    }
    public void generateMipMaps(int textureId, int textureMinFilter, int textureMagFilter) {
        renderer.getOpenGLContext().bindTexture(TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }
    public void enableMipMaps(int textureId, int textureMinFilter, int textureMagFilter) {
        renderer.getOpenGLContext().bindTexture(TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
    }
    
    public void generateMipMapsCubeMap(int textureId) {
        renderer.getOpenGLContext().bindTexture(TEXTURE_CUBE_MAP, textureId);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
    }
    
    public static ByteBuffer getTextureData(int textureId, int mipLevel, int format, ByteBuffer pixels) {
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(TEXTURE_2D, textureId);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, mipLevel, format, GL11.GL_UNSIGNED_BYTE, pixels);
		return pixels;
    }
    
    public static int copyCubeMap(int sourceTextureId, int width, int height, int internalFormat) {
    	int copyTextureId = getTextureId();
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(0, TEXTURE_CUBE_MAP, copyTextureId);

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

//		for(int i = 0; i < 6; i++) {
//			GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
//		}
        GL42.glTexStorage2D(TEXTURE_CUBE_MAP.glTarget, 1, internalFormat, width, height);
		
		GL43.glCopyImageSubData(sourceTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
				copyTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
				width, height, 6);

		return copyTextureId;
    }
    
    public static void deleteTexture(int id) {
		GL11.glDeleteTextures(id);
    }

    //TODO: Add texture filters as params
    public int getCubeMap(int width, int height, int format) {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP);
    }

    public int getCubeMapArray(int width, int height, int format) {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP_ARRAY, 1);
    }
    public int getCubeMapArray(int width, int height, int format, int depth) {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP_ARRAY, depth);
    }

    public int getTexture(int width, int height, int format, GlTextureTarget target) {
        return getTexture(width, height, format, target, 1);
    }

    public int getTexture(int width, int height, int format, GlTextureTarget target, int depth) {
        int textureId = createTextureID();
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(target, textureId);

        setupTextureParameters(target);

        if(target == TEXTURE_CUBE_MAP_ARRAY) {
            GL42.glTexStorage3D(target.glTarget, 1, format, width, height, 6*depth);
        } else {
            GL42.glTexStorage2D(target.glTarget, 1, format, width, height);
        }

        return textureId;
    }

    private void setupTextureParameters(GlTextureTarget target) {
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL30.glGenerateMipmap(target.glTarget);
    }

}