package main.model;

import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.renderer.material.MaterialFactory.MaterialInfo;

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

	private Renderer renderer;
	
	public OBJLoader(Renderer renderer) {
		this.renderer = renderer;
	}

    private static float[] asFloats(Vector3f v) {
        return new float[]{v.x, v.y, v.z};
    }
    
    private String[] removeEmpties(String[] in) {
    	List<String> inList = new ArrayList(Arrays.asList(in));
    	for (int i = 0; i < inList.size(); i++) {
    		String entry = inList.get(i);
			if(entry.isEmpty()) {
				inList.remove(i);
			}
		}
    	return inList.toArray(in);
    }

	public float parseFloat(String line) {
		return Float.valueOf(line);
	}
	
    public Vector3f parseVertex(String line) {
        String[] xyz = removeEmpties(line.split(" "));
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        float z = Float.valueOf(xyz[3]);
        return new Vector3f(x, y, z);
    }
    
    public Vector2f parseTexCoords(String line) {
        String[] xyz = removeEmpties(line.split(" "));
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        return new Vector2f(x, y);
    }

    public Vector3f parseNormal(String line) {
        String[] xyz = removeEmpties(line.split(" "));
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        float z = Float.valueOf(xyz[3]);
        return new Vector3f(x, y, z);
    }

	public Face parseFace(String line) throws Exception {
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
	
    public List<Model> loadTexturedModel(File f) throws Exception {
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
            	renderer.getMaterialFactory().putAll(parseMaterialLib(line, f));
            } else if (line.startsWith("usemtl ")) {
		    	  String materialName = line.replaceAll("usemtl ", "");
		    	  Material material = renderer.getMaterialFactory().get(materialName);
		    	  if(material == null) {
//		    		  LOGGER.log(Level.INFO, "No material found!!!");
		    	  }
		    	  model.setMaterial(material);
//	    		  LOGGER.log(Level.INFO, String.format("Material %s set for %s", material.getName(), model.getName()));
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


	private Map<String, MaterialInfo> parseMaterialLib(String line, File f) {

		Map<String, MaterialInfo> materials = new HashMap<>();
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
		MaterialInfo currentMaterialInfo = null;// = new MaterialInfo();
		
		try {
			while ((materialLine = materialFileReader.readLine()) != null) {
			    if (materialLine.startsWith("#")) {
			    	continue;
			    } else if (materialLine.startsWith("newmtl ")) {
			    	  String name = materialLine.replaceAll("newmtl ", "");
			    	  currentMaterialInfo = new MaterialInfo();
			    	  currentMaterialInfo.name = name;
			    	  materials.put(currentMaterialInfo.name, currentMaterialInfo);
			    	  
			    } else if (materialLine.startsWith("map_Kd ")) {
			    	  String map = materialLine.replaceAll("map_Kd ", "");
			    	  addHelper(currentMaterialInfo, path, map, MAP.DIFFUSE );
			    	  
			    } else if (materialLine.startsWith("map_Ka ")) {
			    	  String map = materialLine.replaceAll("map_Ka ", "");
			    	  addHelper(currentMaterialInfo, path, map, MAP.OCCLUSION );
			    	  
			    } else if (materialLine.startsWith("map_Disp ")) {
			    	  String map = materialLine.replaceAll("map_Disp ", "");
			    	  addHelper(currentMaterialInfo, path, map, MAP.SPECULAR );
			    	  
			    } else if (materialLine.startsWith("map_Ks ")) {
			    	  String map = materialLine.replaceAll("map_Ks ", "");
			    	  addHelper(currentMaterialInfo, path, map, MAP.SPECULAR );
			    	  
			    } else if (materialLine.startsWith("map_bump ")) {
			    	  String map = materialLine.replaceAll("map_bump ", "");
			    	  addHelper(currentMaterialInfo, path, map, MAP.NORMAL );
			    	  
			    } else if (materialLine.startsWith("bump ")) {
			    	  String map = materialLine.replaceAll("bump ", "");
			    	  addHelper(currentMaterialInfo, path, map, MAP.NORMAL );
			    	  
			    } else if (materialLine.startsWith("map_d ")) {
			    	  String map = materialLine.replaceAll("map_d ", "");
			    	  addHelper(currentMaterialInfo, path, map, MAP.NORMAL );
			    	  
			    } else if (materialLine.startsWith("Ka ")) {
			    	  String ambient = materialLine;
			    	  currentMaterialInfo.ambient = parseVertex(ambient);
			    } else if (materialLine.startsWith("Kd ")) {
			    	  String diffuse = materialLine;
			    	  currentMaterialInfo.diffuse = parseVertex(diffuse);
			    } else if (materialLine.startsWith("Ks ")) {
			    	  String specular = materialLine;
			    	  currentMaterialInfo.specular = parseVertex(specular);
			    } else if (materialLine.startsWith("Ns ")) {
			    	  String specularCoefficient = materialLine.replaceAll("Ns ", "");
			    	  currentMaterialInfo.specularCoefficient = parseFloat(specularCoefficient);
			    }
			    // TODO: TRANSPARENCY with "d" and "Tr"
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return materials;
	}

	private void addHelper(MaterialInfo currentMaterialInfo, String path, String name, MAP map) {
		try {
			currentMaterialInfo.maps.put(map, renderer.getTextureFactory().getTexture(path+name));
		} catch (Exception e) {
			e.printStackTrace();
		}
//		LOGGER.log(Level.INFO, String.format("%s to %s as %s",path+name, currentMaterialInfo.name, map ));
	}

	private void parseName(String line, Model model) {
		String name = line.replaceAll("o ", "");
		model.setName(name);
	}
}