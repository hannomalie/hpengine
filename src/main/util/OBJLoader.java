package main.util;

import main.Face;
import main.Material;
import main.Model;
import main.Material.MAP;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.opengl.TextureLoader;
import org.newdawn.slick.util.Log;

import java.io.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Integer.parseInt;
import static main.log.ConsoleLogger.getLogger;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

public class OBJLoader {
	private static Logger LOGGER = getLogger();

    private static float[] asFloats(Vector3f v) {
        return new float[]{v.x, v.y, v.z};
    }
    
    private static String[] removeEmpties(String[] in) {
    	List<String> inList = new ArrayList(Arrays.asList(in));
    	for (int i = 0; i < inList.size(); i++) {
    		String entry = inList.get(i);
			if(entry.isEmpty()) {
				inList.remove(i);
			}
		}
    	return inList.toArray(in);
    }

	public static float parseFloat(String line) {
		return Float.valueOf(line);
	}
	
    public static Vector3f parseVertex(String line) {
        String[] xyz = removeEmpties(line.split(" "));
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        float z = Float.valueOf(xyz[3]);
        return new Vector3f(x, y, z);
    }
    
    public static Vector2f parseTexCoords(String line) {
        String[] xyz = removeEmpties(line.split(" "));
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        return new Vector2f(x, y);
    }

    public static Vector3f parseNormal(String line) {
        String[] xyz = removeEmpties(line.split(" "));
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        float z = Float.valueOf(xyz[3]);
        return new Vector3f(x, y, z);
    }

	public static Face parseFace(String line) {
		String[] faceIndices = line.split(" ");

		String[] firstTriple = (faceIndices[1].split("/"));
		String[] secondTriple = (faceIndices[2].split("/"));
		String[] thirdTriple = (faceIndices[3].split("/"));
		int[] vertexIndices = {
			parseInt(firstTriple[0]),
			parseInt(secondTriple[0]),
			parseInt(thirdTriple[0]),
		};
		int[] texCoordsIndices = {
			parseInt(firstTriple[1]),
			parseInt(secondTriple[1]),
			parseInt(thirdTriple[1]),
			};
		int[] normalIndices = {
			parseInt(firstTriple[2]),
			parseInt(secondTriple[2]),
			parseInt(thirdTriple[2]),
		};
		
		Face face = new Face(vertexIndices, texCoordsIndices, normalIndices);
        return face;
	}

    public static List<Model> loadTexturedModel(File f) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(f));
        List<Model> models = new ArrayList<>();
        Model model = null;// = new Model();
        
//        Map<String, Material> materials = new HashMap();
        ArrayList<Vector3f> vertices = new ArrayList<>();
        ArrayList<Vector2f> texCoords = new ArrayList<>();
        ArrayList<Vector3f> normals = new ArrayList<>();
        
        String line;
        while ((line = reader.readLine()) != null) {
//            if (line.startsWith("#")) {
//                continue;
//            }
            
            if (line.startsWith("mtllib ")) {
            	Material.MATERIALS.putAll(parseMaterialLib(line, f));
            } else if (line.startsWith("usemtl ")) {
		    	  String materialName = line.replaceAll("usemtl ", "");
		    	  Material material = Material.MATERIALS.get(materialName);
		    	  if(material == null) {
		    		  LOGGER.log(Level.INFO, "No material found!!!");
		    	  }
		    	  model.setMaterial(material);
	    		  LOGGER.log(Level.INFO, String.format("Material %s set for %s", material.getName(), model.getName()));
		    } else if (line.startsWith("o ") || line.startsWith("# object ")) {
                if (model != null && model.getMaterial() == null) {
                	int d = 3;
                }
            	model = new Model();
            	model.setName(line);
                model.setVertices(vertices);
                model.setTexCoords(texCoords);
                model.setNormals(normals);

            	models.add(model);
            	parseName(line, model);
            } else if (line.startsWith("v ")) {
            	vertices.add(parseVertex(line));
            } else if (line.startsWith("vt ")) {
            	texCoords.add(parseTexCoords(line));
            } else if (line.startsWith("vn ")) {
            	normals.add(parseVertex(line));
            } else if (line.startsWith("f ")) {
            	model.getFaces().add(parseFace(line));
            }

        }
        reader.close();
        
        return models;
    }


	private static Map<String, Material> parseMaterialLib(String line,
			File f) {

		Map<String, Material> materials = new HashMap<>();
		String[] twoStrings = line.split(" ");
		
		String fileName = twoStrings[1];
		String path = f.getParent() + "\\";
		String finalPath = path + fileName;
		
		BufferedReader materialFileReader = null;
		try {
			materialFileReader = new BufferedReader(new FileReader(finalPath));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		String materialLine;
		String parseMaterialName = "";
		Material currentMaterial = new Material();
		try {
			while ((materialLine = materialFileReader.readLine()) != null) {
			    if (materialLine.startsWith("#")) {
			    	continue;
			    } else if (materialLine.startsWith("newmtl ")) {
			    	  String name = materialLine.replaceAll("newmtl ", "");
			    	  currentMaterial.setup();
			    	  currentMaterial = new Material();
			    	  currentMaterial.setName(name);
			    	  materials.put(name, currentMaterial);
			    	  
			    } else if (materialLine.startsWith("map_Kd ")) {
			    	  String map = materialLine.replaceAll("map_Kd ", "");
			    	  addHelper(currentMaterial, path, map, MAP.DIFFUSE );
			    	  if(currentMaterial.textures.size() == 0) {
			    		  String test = "";
			    	  }
			    	  
			    } else if (materialLine.startsWith("map_Ka ")) {
			    	  String map = materialLine.replaceAll("map_Ka ", "");
			    	  addHelper(currentMaterial, path, map, MAP.OCCLUSION );
			    	  
			    } else if (materialLine.startsWith("map_Disp ")) {
			    	  String map = materialLine.replaceAll("map_Disp ", "");
			    	  addHelper(currentMaterial, path, map, MAP.SPECULAR );
			    	  
			    } else if (materialLine.startsWith("map_bump ")) {
			    	  String map = materialLine.replaceAll("map_bump ", "");
			    	  addHelper(currentMaterial, path, map, MAP.NORMAL );
			    	  
			    } else if (materialLine.startsWith("bump ")) {
			    	  String map = materialLine.replaceAll("bump ", "");
			    	  addHelper(currentMaterial, path, map, MAP.NORMAL );
			    	  
			    } else if (materialLine.startsWith("map_d ")) {
			    	  String map = materialLine.replaceAll("map_d ", "");
			    	  addHelper(currentMaterial, path, map, MAP.NORMAL );
			    	  
			    } else if (materialLine.startsWith("Ka ")) {
			    	  String ambient = materialLine;
			    	  currentMaterial.setAmbient(parseVertex(ambient));
			    } else if (materialLine.startsWith("Kd ")) {
			    	  String diffuse = materialLine;
			    	  currentMaterial.setDiffuse(parseVertex(diffuse));
			    } else if (materialLine.startsWith("Ks ")) {
			    	  String specular = materialLine;
			    	  currentMaterial.setSpecular(parseVertex(specular));
			    } else if (materialLine.startsWith("Ns ")) {
			    	  String specularCoefficient = materialLine.replaceAll("Ns ", "");
			    	  currentMaterial.setSpecularCoefficient(parseFloat(specularCoefficient));
			    }
			    // TODO: TRANSPARENCY with "d" and "Tr"
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return materials;
	}

	private static void addHelper(Material currentMaterial, String path, String name, MAP map) {
  	  
  	  currentMaterial.loadAndAddTexture(map, path+name);
  	  LOGGER.log(Level.INFO, String.format("%s to %s as %s",path+name, currentMaterial.getName(), map ));
	}

	private static Material parseMaterial(File f, String line) {
		return null;
//		String materialFileName = line.split(" ")[1];
//        File materialFile = new File(f.getParentFile().getAbsolutePath() + "/" + materialFileName);
//        BufferedReader materialFileReader = new BufferedReader(new FileReader(materialFile));
//        String materialLine;
//        Material parseMaterial = new Material(renderer);
//        String parseMaterialName = "";
//        while ((materialLine = materialFileReader.readLine()) != null) {
//            if (materialLine.startsWith("#") || materialLine.startsWith("")) {
//                continue;
//            }
//            if (materialLine.startsWith("newmtl ")) {
	}

	private static void parseName(String line, Model model) {
		String name = line.replaceAll("o ", "");
		model.setName(name);
	}
}