package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.log.ConsoleLogger;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.texture.TextureFactory;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.renderer.material.MaterialInfo;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Integer.parseInt;

public class OBJLoader {
    private static Logger LOGGER = ConsoleLogger.getLogger();

    public enum State {
        READING_VERTEX,
        READING_UV,
        READING_NORMAL,
        READING_FACE,
        READING_MATERIALLIB
    }

    private State currentState;

    public OBJLoader() {

    }

    private static float[] asFloats(Vector3f v) {
        return new float[]{v.x, v.y, v.z};
    }

    private String[] removeEmpties(String[] in) {
        List<String> inList = new ArrayList(Arrays.asList(in));
        for (int i = 0; i < inList.size(); i++) {
            String entry = inList.get(i);
            if (entry.isEmpty()) {
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
        int[] texCoordsIndices;
        try {
            texCoordsIndices = new int[]{
                    parseInt(firstTriple[1]),
                    parseInt(secondTriple[1]),
                    parseInt(thirdTriple[1]),
            };
        } catch (Exception e) {
            texCoordsIndices = new int[]{-1, -1, -1};
        }

        int[] normalIndices = {
                parseInt(firstTriple[2]),
                parseInt(secondTriple[2]),
                parseInt(thirdTriple[2]),
        };

        Face face = new Face(vertexIndices, texCoordsIndices, normalIndices);
        return face;
    }

    public Model loadTexturedModel(File f) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(f));
        Model resultModel = new Model();

        Mesh currentMesh = null;
        Material currentMaterial = null;

        int usemtlCounter = 0;

        String line;
        while ((line = reader.readLine()) != null) {

            String[] tokens = line.split(" ");

            String firstToken = "";
            if(tokens != null && tokens.length > 0) {
                firstToken = tokens[0];
            }
            if ("mtllib".equals(firstToken)) {
                MaterialFactory.getInstance().putAll(parseMaterialLib(line, f));
            } else if ("usemtl".equals(firstToken)) {
                String materialName = line.replaceAll("usemtl ", "");
                currentMaterial = MaterialFactory.getInstance().getMaterial(materialName);
                if (currentMaterial == null) {
                    LOGGER.log(Level.INFO, "No material found!!!");
                    currentMaterial = MaterialFactory.getInstance().getDefaultMaterial();
                }
                usemtlCounter++;
            } else if ("o".equals(firstToken) || "g".equals(firstToken) || line.startsWith("# object ")) {
                currentMesh = newMeshHelper(resultModel.getVertices(), resultModel.getTexCoords(), resultModel.getNormals(), line, line.replaceAll("o ", "").replaceAll("# object ", "").replaceAll("g ", ""));
                resultModel.addMesh(currentMesh);
                usemtlCounter = 0;
            } else if ("v".equals(firstToken)) {
                setCurrentState(State.READING_VERTEX);
                resultModel.addVertex(parseVertex(line));
            } else if ("vt".equals(firstToken)) {
                setCurrentState(State.READING_UV);
                resultModel.addTexCoords(parseTexCoords(line));
            } else if ("vn".equals(firstToken)) {
                setCurrentState(State.READING_NORMAL);
                resultModel.addNormal(parseVertex(line));
            } else if ("f".equals(firstToken)) {
                setCurrentState(State.READING_FACE);
                if(usemtlCounter > 1) {
                    currentMesh = newMeshHelper(resultModel.getVertices(), resultModel.getTexCoords(), resultModel.getNormals(), line, currentMesh.getName() + new Random().nextInt());
                    resultModel.addMesh(currentMesh);
                    usemtlCounter = 0;
                }
                currentMesh.setMaterial(currentMaterial);
                currentMesh.getIndexedFaces().add(parseFace(line));
            }

        }
        reader.close();

        resultModel.init();
        return resultModel;
    }

    private Mesh newMeshHelper(ArrayList<Vector3f> vertices, ArrayList<Vector2f> texCoords,
                               ArrayList<Vector3f> normals, String line, String name) {
        Mesh mesh = new Mesh(line, vertices, texCoords, normals);
        mesh.setName(name);
        return mesh;
    }


    private Map<String, MaterialInfo> parseMaterialLib(String line, File f) {
        Map<String, MaterialInfo> materials = new HashMap<>();
        String[] twoStrings = line.split(" ");

        String fileName = twoStrings[1];
        String path = f.getParent() + "/";
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

                String[] tokens = materialLine.split(" ");
                String firstToken = tokens[0];

                if (firstToken.startsWith("#")) {
                    continue;
                } else if ("newmtl".equals(firstToken)) {
                    String name = materialLine.replaceAll("newmtl ", "");
                    currentMaterialInfo = new MaterialInfo();
                    currentMaterialInfo.name = name;
                    materials.put(currentMaterialInfo.name, currentMaterialInfo);

                } else if ("map_Kd".equals(firstToken)) {
                    String map = materialLine.replaceAll("map_Kd ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.DIFFUSE);

                } else if ("map_Ka".equals(firstToken)) {
                    String map = materialLine.replaceAll("map_Ka ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.OCCLUSION);

                } else if ("map_Disp".equals(firstToken)) {
                    String map = materialLine.replaceAll("map_Disp ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.SPECULAR);
//			    	  addHelper(currentMaterialInfo, path, map, MAP.ROUGHNESS );

                } else if ("map_Ks".equals(firstToken)) {
                    String map = materialLine.replaceAll("map_Ks ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.SPECULAR);
//			    	  addHelper(currentMaterialInfo, path, map, MAP.ROUGHNESS );

                } else if ("map_Ns".equals(firstToken)) {
                    String map = materialLine.replaceAll("map_Ns ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.ROUGHNESS);

                } else if ("map_bump".equals(firstToken)) {
                    String map = materialLine.replaceAll("map_bump ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.NORMAL);

                } else if ("bump".equals(firstToken)) {
                    String map = materialLine.replaceAll("bump ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.NORMAL);

                } else if ("map_d".equals(firstToken)) {
                    String map = materialLine.replaceAll("map_d ", "");
                    addHelper(currentMaterialInfo, path, map, Material.MAP.NORMAL);

                } else if ("Kd".equals(firstToken)) {
                    String diffuse = materialLine;
                    currentMaterialInfo.diffuse = parseVertex(diffuse);
                } else if ("Kr".equals(firstToken)) {
                    String roughness = materialLine.replaceAll("rough ", "");
                    currentMaterialInfo.roughness = parseFloat(roughness);
                } else if ("Ks".equals(firstToken)) {
                    String specular = materialLine;
                    //currentMaterialInfo.specular = parseVertex(specular);
                    // Physically based tmaterials translate specular to roughness
//			    	  currentMaterialInfo.roughness = 1-parseVertex(specular).x;
                } else if ("Ns".equals(firstToken)) {
                    String specularCoefficient = materialLine.replaceAll("Ns ", "");
//			    	  currentMaterialInfo.roughness = 1-(parseFloat(specularCoefficient)/1000 + 1);
//			    	  currentMaterialInfo.specularCoefficient = parseFloat(specularCoefficient);
                }
                // TODO: TRANSPARENCY with "d" and "Tr"
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return materials;
    }

    private void addHelper(MaterialInfo currentMaterialInfo, String path, String name, Material.MAP map) {
        currentMaterialInfo.maps.put(map, TextureFactory.getInstance().getTexture(path + name, map == Material.MAP.DIFFUSE));
    }

    private void parseName(String line, Mesh mesh) {
        String name = line.replaceAll("o ", "");
        mesh.setName(name);
    }

    private State getCurrentState() {
        return currentState;
    }

    private void setCurrentState(State currentState) {
        this.currentState = currentState;
    }
}
