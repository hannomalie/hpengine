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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import engine.World;
import renderer.DeferredRenderer;
import renderer.Renderer;
import renderer.Result;
import renderer.command.Command;
import renderer.material.Material;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.util.vector.Vector2f;

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

        defaultTexture = getTexture("hp/assets/models/textures/gi_flag.png");
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
       return GL11.glGenTextures(); 
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
        Texture tex = TEXTURES.get(resourceName);
        
        if (tex != null) {
            return tex;
        }
        
        if (texturePreCompiled(resourceName)) {
        	tex = Texture.read(resourceName, createTextureID());
        	if (tex != null) {
                generateMipMaps(tex, Material.MIPMAP_DEFAULT);
                TEXTURES.put(resourceName,tex);
                return tex;
            }
        }

        try {
            tex = getTexture(resourceName,
                    GL11.GL_TEXTURE_2D, // target
                    GL11.GL_RGBA,     // dst pixel format
                    GL11.GL_LINEAR, // min filter (unused)
                    GL11.GL_LINEAR, false, srgba);
        } catch (IOException e) {
            Logger.getGlobal().severe("Texture not found: " + resourceName + ". Default texture returned...");
            tex = defaultTexture;
        }

        TEXTURES.put(resourceName,tex);
        return tex;
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
        				 GL13.GL_TEXTURE_CUBE_MAP, // target
                         GL11.GL_RGBA,     // dst pixel format
                         GL11.GL_LINEAR_MIPMAP_LINEAR, // min filter (unused)
                         GL11.GL_LINEAR, false);

        TEXTURES.put(resourceName + "_cube",tex);
        System.out.println("Precompiled " + CubeMap.write(tex, resourceName));
        return tex;
    }

	public Texture getTextureAsStream(String resourceName) throws IOException {
    	Texture tex = (Texture) TEXTURES.get(resourceName);
        
        if (tex != null) {
            return tex;
        }
        
        if (texturePreCompiled(resourceName)) {
        	tex = Texture.read(resourceName, createTextureID());
        	if (tex != null) {
                generateMipMaps(tex, Material.MIPMAP_DEFAULT);
                TEXTURES.put(resourceName,tex);
                return tex;
            }
        }
        
        tex = getTexture(resourceName,
                         GL11.GL_TEXTURE_2D, // target
                         GL11.GL_RGBA,     // dst pixel format
                         GL11.GL_LINEAR_MIPMAP_LINEAR, // min filter (unused)
                         GL11.GL_LINEAR, true);
        
        TEXTURES.put(resourceName,tex);

        return tex;
    }
	
	public CubeMap getCubeMapAsStream(String resourceName) throws IOException {
    	Texture tex = (Texture) TEXTURES.get(resourceName+ "_cube");
        
        if (tex != null && tex instanceof CubeMap) {
            return (CubeMap) tex;
        }
        
        if (cubeMapPreCompiled(resourceName)) {
        	tex = Texture.read(resourceName, createTextureID());
        	if (tex != null) {
                generateMipMaps(tex, Material.MIPMAP_DEFAULT);
                TEXTURES.put(resourceName+ "_cube",tex);
                return (CubeMap) tex;
            }
        }
        
        tex = getCubeMap(resourceName,
                         GL13.GL_TEXTURE_CUBE_MAP, // target
                         GL11.GL_RGBA,     // dst pixel format
                         GL11.GL_LINEAR, // min filter (unused)
                         GL11.GL_LINEAR, true);
        
        TEXTURES.put(resourceName+ "_cube",tex);
        return (CubeMap) tex;
    }
    
    /**
     * Load a texture into OpenGL from a image reference on
     * disk.
     *
     * @param resourceName The location of the resource to load
     * @param target The GL target to load the texture against
     * @param dstPixelFormat The pixel format of the screen
     * @param minFilter The minimising filter
     * @param magFilter The magnification filter
     * @param asStream 
     * @return The loaded texture
     * @throws IOException Indicates a failure to access the resource
     */
    public Texture getTexture(String resourceName, 
            int target, 
            int dstPixelFormat, 
            int minFilter, 
            int magFilter, boolean asStream) throws IOException {
    	
    	return getTexture(resourceName, target, dstPixelFormat, minFilter, magFilter, asStream, false);
    }

    public Texture getTexture(String resourceName, 
                              int target, 
                              int dstPixelFormat, 
                              int minFilter, 
                              int magFilter, boolean asStream, boolean srga) throws IOException 
    { 
        int srcPixelFormat = 0;
        
        // create the texture ID for this texture 
        Texture texture = new Texture(resourceName, target,createTextureID()); 
        
        texture.bind();
        
//        long start = System.currentTimeMillis();
        
        BufferedImage bufferedImage = null;
        if (asStream) {
            bufferedImage = loadImageAsStream(resourceName);
        } else {
            bufferedImage = loadImage(resourceName);	
        }
        if(bufferedImage == null) {
            throw new IOException("Image not found " + resourceName);
        }
        texture.setWidth(bufferedImage.getWidth());
        texture.setHeight(bufferedImage.getHeight());
        texture.setMinFilter(minFilter);
        texture.setMagFilter(magFilter);
        
        if (bufferedImage.getColorModel().hasAlpha()) {
            srcPixelFormat = GL11.GL_RGBA;
        } else {
            srcPixelFormat = GL11.GL_RGB;
        }

        texture.setDstPixelFormat(dstPixelFormat);
        texture.setSrcPixelFormat(srcPixelFormat);
        
        // convert that image into a byte buffer of texture data 
        ByteBuffer textureBuffer = convertImageData(bufferedImage,texture);
        
        renderer.addCommand(new Command<Result>() {
			@Override
			public Result execute(World world) {
		        texture.upload(textureBuffer, srga);
		        return new Result(new Object());
			}
		});
//        texture.upload(textureBuffer, srga);
        
//        System.out.println("TEXTURE READ NEW IN " + (System.currentTimeMillis() - start + " ms"));
//        generateMipMaps(texture, Material.MIPMAP_DEFAULT);
        
        return texture; 
    }
    
    private CubeMap getCubeMap(String resourceName, 
					            int target, 
					            int dstPixelFormat, 
					            int minFilter, 
					            int magFilter, boolean asStream) throws IOException {
    	
    	
    	 int srcPixelFormat = 0;
         
         // create the texture ID for this texture 
         int textureID = createTextureID(); 
         CubeMap cubeMap = new CubeMap(resourceName, target, textureID); 
         
         // bind this texture 
         GL11.glBindTexture(target, textureID);
         
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
     * Get the closest greater power of 2 to the fold number
     * 
     * @param fold The target number
     * @return The power of 2
     */
    private int get2Fold(int fold) {
        int ret = 2;
        while (ret < fold) {
            ret *= 2;
        }
        return ret;
    } 
    
    /**
     * Convert the buffered image to a texture
     *
     * @param bufferedImage The image to convert to a texture
     * @param texture The texture to store the data into
     * @return A buffer containing the data
     */
    private ByteBuffer convertImageData(BufferedImage bufferedImage,Texture texture) { 
        ByteBuffer imageBuffer = null; 
        WritableRaster raster;
        BufferedImage texImage;
        
        int texWidth = 2;
        int texHeight = 2;
        
        // find the closest power of 2 for the width and height
        // of the produced texture
        while (texWidth < bufferedImage.getWidth()) {
            texWidth *= 2;
        }
        while (texHeight < bufferedImage.getHeight()) {
            texHeight *= 2;
        }
        
        texture.setTextureHeight(texHeight);
        texture.setTextureWidth(texWidth);
        
        // create a raster that can be used by OpenGL as a source
        // for a texture
        if (bufferedImage.getColorModel().hasAlpha()) {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,texWidth,texHeight,4,null);
            texImage = new BufferedImage(glAlphaColorModel,raster,false,new Hashtable());
        } else {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,texWidth,texHeight,3,null);
            texImage = new BufferedImage(glColorModel,raster,false,new Hashtable());
        }
            
        // copy the source image into the produced image
        Graphics g = texImage.getGraphics();
        g.setColor(new Color(0f,0f,0f,0f));
        g.fillRect(0,0,texWidth,texHeight);
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
        
        int texWidth = 2;
        int texHeight = 2;
        
        // find the closest power of 2 for the width and height
        // of the produced texture
        while (texWidth < bufferedImage.getWidth()) {
            texWidth *= 2;
        }
        while (texHeight < bufferedImage.getHeight()) {
            texHeight *= 2;
        }

    	cubeMap.setTextureHeight(texHeight);
    	cubeMap.setTextureWidth(texWidth);

    	int tileWidthPoT = get2Fold(texWidth/4);
    	int tileHeightPoT = get2Fold(texHeight/3);
    	
        for(int i = 0; i < 6; i++) {

            //Vector2f[] topLeftBottomRight = getRectForFaceIndex(i, texWidth, texHeight);
        	Vector2f[] topLeftBottomRight = getRectForFaceIndex(i, bufferedImage.getWidth(), bufferedImage.getHeight());
            
            if (bufferedImage.getColorModel().hasAlpha()) {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,tileWidthPoT,tileHeightPoT,4,null);
                texImage = new BufferedImage(glAlphaColorModel,raster,false,new Hashtable());
            } else {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,tileWidthPoT,tileHeightPoT,3,null);
                texImage = new BufferedImage(glColorModel,raster,false,new Hashtable());
            }
            
            Graphics g = texImage.getGraphics();
            g.setColor(new Color(0f,0f,0f,0f));
            g.fillRect(0,0,tileWidthPoT,tileHeightPoT);
            
            g.drawImage(bufferedImage,0,0,get2Fold(texWidth/4), get2Fold(texHeight/3)/2, (int)topLeftBottomRight[0].x,(int)topLeftBottomRight[0].y,
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
    		tempBuffer = ByteBuffer.allocateDirect(data.length);
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
    private BufferedImage loadImage(String ref) throws IOException 
    {
        URL url = TextureFactory.class.getClassLoader().getResource(ref);
        
        if (url == null) {
//            throw new IOException("Cannot find: "+ref);
            return loadImageAsStream(ref);
        }
        
        BufferedImage bufferedImage = ImageIO.read(new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(ref)));

        return bufferedImage;
    }

    private BufferedImage loadImageAsStream(String ref) throws IOException 
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
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId); 
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }
    public void enableMipMaps(int textureId, int textureMinFilter, int textureMagFilter) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId); 
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
    }
    
    public void generateMipMapsCubeMap(int textureId) {
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, textureId);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
    }
    
    public ByteBuffer getTextureData(int textureId, int mipLevel, int width, int height, int format, ByteBuffer pixels) {
    	GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, mipLevel, format, GL11.GL_UNSIGNED_BYTE, pixels);
		return pixels;
    }
    
    public static int copyCubeMap(int sourceTextureId, int width, int height, int internalFormat) {
    	int copyTextureId = GL11.glGenTextures();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, copyTextureId);
		
		for(int i = 0; i < 6; i++) {
			GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		}
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE); 
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		
		GL43.glCopyImageSubData(sourceTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
				copyTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
				width, height, 6);

		return copyTextureId;
    }
    
    public static void deleteTexture(int id) {
		GL11.glDeleteTextures(id);
    }

}