package main.util;

import main.Face;
import main.Material;
import main.Model;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.newdawn.slick.opengl.TextureLoader;

import java.io.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

public class OBJLoader {

    private static float[] asFloats(Vector3f v) {
        return new float[]{v.x, v.y, v.z};
    }

    public static Vector3f parseVertex(String line) {
        String[] xyz = line.split(" ");
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        float z = Float.valueOf(xyz[3]);
        return new Vector3f(x, y, z);
    }
    
    public static Vector2f parseTexCoords(String line) {
        String[] xyz = line.split(" ");
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        return new Vector2f(x, y);
    }

    public static Vector3f parseNormal(String line) {
        String[] xyz = line.split(" ");
        float x = Float.valueOf(xyz[1]);
        float y = Float.valueOf(xyz[2]);
        float z = Float.valueOf(xyz[3]);
        return new Vector3f(x, y, z);
    }

	public static Face parseFace(String line) {
		String[] faceIndices = line.split(" ");

		int[] vertexIndices = {
			Integer.parseInt((faceIndices[1].split("/"))[0]),
			Integer.parseInt((faceIndices[2].split("/"))[0]),
			Integer.parseInt((faceIndices[3].split("/"))[0]),
		};
		int[] texCoordsIndices = {
				Integer.parseInt((faceIndices[1].split("/"))[1]),
				Integer.parseInt((faceIndices[2].split("/"))[1]),
				Integer.parseInt((faceIndices[3].split("/"))[1]),
			};
		int[] normalIndices = {
			Integer.parseInt((faceIndices[1].split("/"))[2]),
			Integer.parseInt((faceIndices[2].split("/"))[2]),
			Integer.parseInt((faceIndices[3].split("/"))[2]),
		};
		
		Face face = new Face(vertexIndices, texCoordsIndices, normalIndices);
        return face;
	}

    public static Model loadTexturedModel(File f) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(f));
        Model model = new Model();
        Model.Material currentMaterial = new Model.Material();

        ArrayList<Vector3f> vertices = new ArrayList<>();
        ArrayList<Vector2f> texCoords = new ArrayList<>();
        ArrayList<Vector3f> normals = new ArrayList<>();
        
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            
            if (line.startsWith("mtllib ")) {
            	// TODO
            } else if (line.startsWith("o ")) {
            	parseName(line, model);
            } else if (line.startsWith("v ")) {
            	vertices.add(parseVertex(line));
            } else if (line.startsWith("vt ")) {
            	texCoords.add(parseTexCoords(line));
            } else if (line.startsWith("vn ")) {
            	normals.add(parseVertex(line));
            } else if (line.startsWith("f ")) {
            	model.getFaces().add(parseFace(line));
            } else if (line.startsWith("mtllib ")) {
//            	model.setMaterial(parseMaterial(line));
            }

            
            /*
            if (line.startsWith("mtllib ")) {
                String materialFileName = line.split(" ")[1];
                File materialFile = new File(f.getParentFile().getAbsolutePath() + "/" + materialFileName);
                BufferedReader materialFileReader = new BufferedReader(new FileReader(materialFile));
                String materialLine;
                Model.Material parseMaterial = new Model.Material();
                String parseMaterialName = "";
                while ((materialLine = materialFileReader.readLine()) != null) {
                    if (materialLine.startsWith("#") || materialLine.startsWith("")) {
                        continue;
                    }
                    if (materialLine.startsWith("newmtl ")) {
                        if (!parseMaterialName.equals("")) {
                            m.getMaterials().put(parseMaterialName, parseMaterial);
                        }
                        parseMaterialName = materialLine.split(" ")[1];
                        parseMaterial = new Model.Material();
                    } else if (materialLine.startsWith("Ns ")) {
                        parseMaterial.specularCoefficient = Float.valueOf(materialLine.split(" ")[1]);
                    } else if (materialLine.startsWith("Ka ")) {
                        String[] rgb = materialLine.split(" ");
                        parseMaterial.ambientColour[0] = Float.valueOf(rgb[1]);
                        parseMaterial.ambientColour[1] = Float.valueOf(rgb[2]);
                        parseMaterial.ambientColour[2] = Float.valueOf(rgb[3]);
                    } else if (materialLine.startsWith("Ks ")) {
                        String[] rgb = materialLine.split(" ");
                        parseMaterial.specularColour[0] = Float.valueOf(rgb[1]);
                        parseMaterial.specularColour[1] = Float.valueOf(rgb[2]);
                        parseMaterial.specularColour[2] = Float.valueOf(rgb[3]);
                    } else if (materialLine.startsWith("Kd ")) {
                        String[] rgb = materialLine.split(" ");
                        parseMaterial.diffuseColour[0] = Float.valueOf(rgb[1]);
                        parseMaterial.diffuseColour[1] = Float.valueOf(rgb[2]);
                        parseMaterial.diffuseColour[2] = Float.valueOf(rgb[3]);
                    } else if (materialLine.startsWith("map_Kd")) {
                        parseMaterial.texture = TextureLoader.getTexture("PNG",
                                new FileInputStream(new File(f.getParentFile().getAbsolutePath() + "/" + materialLine
                                        .split(" ")[1])));
                    } else {
                        System.err.println("[MTL] Unknown Line: " + materialLine);
                    }
                }
                m.getMaterials().put(parseMaterialName, parseMaterial);
                materialFileReader.close();
            } else if (line.startsWith("usemtl ")) {
                currentMaterial = m.getMaterials().get(line.split(" ")[1]);
            } else if (line.startsWith("v ")) {
                String[] xyz = line.split(" ");
                float x = Float.valueOf(xyz[1]);
                float y = Float.valueOf(xyz[2]);
                float z = Float.valueOf(xyz[3]);
                m.getVertices().add(new Vector3f(x, y, z));
   
            } else if (line.startsWith("vn ")) {
                String[] xyz = line.split(" ");
                float x = Float.valueOf(xyz[1]);
                float y = Float.valueOf(xyz[2]);
                float z = Float.valueOf(xyz[3]);
                m.getNormals().add(new Vector3f(x, y, z));
            } else if (line.startsWith("vt ")) {
                String[] xyz = line.split(" ");
                float s = Float.valueOf(xyz[1]);
                float t = Float.valueOf(xyz[2]);
                m.getTextureCoordinates().add(new Vector2f(s, t));
            } else if (line.startsWith("f ")) {
                String[] faceIndices = line.split(" ");
                int[] vertexIndicesArray = {Integer.parseInt(faceIndices[1].split("/")[0]),
                        Integer.parseInt(faceIndices[2].split("/")[0]), Integer.parseInt(faceIndices[3].split("/")[0])};
                int[] textureCoordinateIndicesArray = {-1, -1, -1};
                if (m.hasTextureCoordinates()) {
                    textureCoordinateIndicesArray[0] = Integer.parseInt(faceIndices[1].split("/")[1]);
                    textureCoordinateIndicesArray[1] = Integer.parseInt(faceIndices[2].split("/")[1]);
                    textureCoordinateIndicesArray[2] = Integer.parseInt(faceIndices[3].split("/")[1]);
                }
                int[] normalIndicesArray = {0, 0, 0};
                if (m.hasNormals()) {
                    normalIndicesArray[0] = Integer.parseInt(faceIndices[1].split("/")[2]);
                    normalIndicesArray[1] = Integer.parseInt(faceIndices[2].split("/")[2]);
                    normalIndicesArray[2] = Integer.parseInt(faceIndices[3].split("/")[2]);
                }
                //                Vector3f vertexIndices = new Vector3f(Float.valueOf(faceIndices[1].split("/")[0]),
                //                        Float.valueOf(faceIndices[2].split("/")[0]),
                // Float.valueOf(faceIndices[3].split("/")[0]));
                //                Vector3f normalIndices = new Vector3f(Float.valueOf(faceIndices[1].split("/")[2]),
                //                        Float.valueOf(faceIndices[2].split("/")[2]),
                // Float.valueOf(faceIndices[3].split("/")[2]));
                m.getFaces().add(new Model.Face(vertexIndicesArray, normalIndicesArray,
                        textureCoordinateIndicesArray, currentMaterial));
            } else if (line.startsWith("s ")) {
                boolean enableSmoothShading = !line.contains("off");
                m.setSmoothShadingEnabled(enableSmoothShading);
            } else {
                System.err.println("[OBJ] Unknown Line: " + line);
            }*/
        }
        reader.close();

        model.setVertices(vertices);
        model.setTexCoords(texCoords);
        model.setNormals(normals);
        
        return model;
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