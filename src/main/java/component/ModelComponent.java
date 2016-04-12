package component;

import camera.Camera;
import config.Config;
import engine.AppContext;
import engine.Drawable;
import engine.model.DataChannels;
import engine.model.Face;
import engine.model.Model;
import engine.model.VertexBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.OpenGLContext;
import renderer.constants.GlCap;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import shader.Program;
import shader.ProgramFactory;
import texture.Texture;

import java.io.Serializable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ModelComponent extends BaseComponent implements Drawable, Serializable {
    private static final long serialVersionUID = 1L;
    private static final boolean USE_PRECOMPUTED_TANGENTSPACE = false;

    private static ExecutorService service = Executors.newFixedThreadPool(4);

    private Model model;

    public boolean instanced = false;

    transient protected VertexBuffer vertexBuffer;
    private float[] floatArray;
    private int[] indices;

    protected String materialName = "";

    public static EnumSet<DataChannels> DEFAULTCHANNELS = USE_PRECOMPUTED_TANGENTSPACE ? EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL,
            DataChannels.TANGENT,
            DataChannels.BINORMAL
            ) : EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.TEXCOORD,
            DataChannels.NORMAL);
    public static EnumSet<DataChannels> DEPTHCHANNELS = EnumSet.of(
            DataChannels.POSITION3,
            DataChannels.NORMAL
    );
    public static EnumSet<DataChannels> SHADOWCHANNELS = EnumSet.of(
            DataChannels.POSITION3);
    public static EnumSet<DataChannels> POSITIONCHANNEL = EnumSet.of(
            DataChannels.POSITION3);
    private int nbCollapsedTri;
    private List<VertexInfo> vertexList;


    public ModelComponent(Model model) {
        this(model, model.getMaterial());
    }
    public ModelComponent(Model model, Material material) {
        this(model, material.getName());
    }
    public ModelComponent(Model model, String materialName) {
        this.materialName = materialName;
        this.model = model;
    }
    @Override
    public int draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram) {
        return draw(camera, modelMatrix, firstPassProgram, AppContext.getInstance().getScene().getEntities().indexOf(getEntity()), getEntity().isVisible(), getEntity().isSelected());
    }
    @Override
    public int draw(Camera camera) {
        return draw(camera, getEntity().getModelMatrixAsBuffer(), ProgramFactory.getInstance().getFirstpassDefaultProgram(), AppContext.getInstance().getScene().getEntities().indexOf(getEntity()), getEntity().isVisible(), getEntity().isSelected());
    }

    @Override
    public int draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected) {
        return draw(camera, modelMatrix, firstPassProgram, entityIndex, isVisible, isSelected, false);
    }

    @Override
    public int draw(Camera camera, FloatBuffer modelMatrix, Program firstPassProgram, int entityIndex, boolean isVisible, boolean isSelected, boolean drawLines) {

        if(!isVisible) {
            return 0;
        }

        if (firstPassProgram == null) {
            return 0;
        }

        Program currentProgram = firstPassProgram;
//        currentProgram.setUniform("isInstanced", instanced);
        currentProgram.setUniform("entityIndex", entityIndex);
        currentProgram.setUniform("materialIndex", MaterialFactory.getInstance().indexOf(MaterialFactory.getInstance().get(materialName)));
//        currentProgram.setUniform("isSelected", isSelected);
//        currentProgram.setUniformAsMatrix4("modelMatrix", modelMatrix);

        // TODO: Implement strategy pattern
        float distanceToCamera = Vector3f.sub(camera.getWorldPosition(), getEntity().getCenterWorld(), null).length();
//        System.out.println("boundingSphere " + model.getBoundingSphereRadius());
//        System.out.println("distanceToCamera " + distanceToCamera);
        boolean isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f*model.getBoundingSphereRadius();
        getMaterial().setTexturesActive(currentProgram, isInReachForTextureLoading);

        if(getMaterial().getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
            OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
        } else {
            OpenGLContext.getInstance().enable(GlCap.CULL_FACE);
        }
//        if(instanced) {
//            return vertexBuffer.drawInstanced(10);
//        } else
        if (drawLines) {
            return vertexBuffer.drawDebug(2);
        } else {
            return vertexBuffer.draw();
        }
    }

    private void setTexturesUsed() {
        for(Texture texture : getMaterial().getTextures()) {
            texture.setUsedNow();
        }
    }

    public int drawDebug(Program program, FloatBuffer modelMatrix) {

        if(!getEntity().isVisible()) {
            return 0;
        }

        program.setUniformAsMatrix4("modelMatrix", modelMatrix);

        model.getMaterial().setTexturesActive(program);
        vertexBuffer.drawDebug();
        return 0;
    }


    public Material getMaterial() {
        Material material = MaterialFactory.getInstance().get(materialName);
        if(material == null) {
            Logger.getGlobal().info("Material null, default is applied");
            return MaterialFactory.getInstance().getDefaultMaterial();
        }
        return material;
    }
    public void setMaterial(String materialName) {
        this.materialName = materialName;
        model.setMaterial(MaterialFactory.getInstance().get(materialName));
    }

    @Override
    public void init() {
        super.init();
        createFloatArray(model);
        createVertexBuffer();
        initialized = true;
    }

    static class EdgeInfo {
        final Face a;
        final Face b;
        final int x;
        final int y;
        float cost;

        EdgeInfo(Face a, Face b, int x, int y) {
            this.a = a;
            this.b = b;
            this.x = x;
            this.y = y;
        }
    }

    static class VertexInfo {
        public static final float NEVER_COLLAPSE_COST = Float.MAX_VALUE;
        private static final float UNINITIALIZED_COLLAPSE_COST = Float.POSITIVE_INFINITY;

        public VertexInfo(Vector3f vertex, int index) {
            this.position = vertex;
            this.index = index;
        }

        Vector3f position = new Vector3f();
        float collapseCost = UNINITIALIZED_COLLAPSE_COST;
        List<Edge> edges = new ArrayList<>();
        Set<Face> faces = Collections.synchronizedSet(new HashSet<>());
        int collapseTo;
        boolean isSeam;
        int index;//index in the buffer for debugging

        @Override
        public String toString() {
            return index + " : " + position.toString();
        }

        public void addEdgeIfAbsent(Edge edge) {
            if(!edges.contains(edge)) {
                edges.add(edge);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof VertexInfo)) {
                return false;
            }
            return position.equals(((VertexInfo) obj).position);
        }

        @Override
        public int hashCode() {
            return super.hashCode() + index;
        }

        public boolean isValid() {
            return edges.size() >=2;
        }
    }

    private class Edge {
        private static final float UNINITIALIZED_COLLAPSE_COST = Float.POSITIVE_INFINITY;

        VertexInfo destination;
        float collapseCost = UNINITIALIZED_COLLAPSE_COST;
        int refCount;

        public Edge(VertexInfo destination) {
            this.destination = destination;
        }

        public void set(Edge other) {
            destination = other.destination;
            collapseCost = other.collapseCost;
            refCount = other.refCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Edge)) {
                return false;
            }
            return destination == ((Edge) obj).destination;
        }

        @Override
        public int hashCode() {
            return destination.hashCode();
        }

        @Override
        public String toString() {
            return "Edge{" + "collapsTo " + destination.index + '}';
        }
    }

    public void createFloatArray(Model model) {

        List<Vector3f> verticesTemp = model.getVertices();
        List<Vector2f> texcoordsTemp = model.getTexCoords();
        List<Vector3f> normalsTemp = model.getNormals();
        List<Face> facesTemp = model.getFaces();

        List<Float[]> vertexBuffer = new ArrayList<>();

        ////////////// INDICES -1 BECAUSE OBJ STARTS WITH 1 INSTEAD OF ZERO

        List<Float> values = new ArrayList<>();
        int valuesPerVertex = USE_PRECOMPUTED_TANGENTSPACE ? 14 : 8;

        for (int i = 0; i < facesTemp.size(); i++) {
            Face face = facesTemp.get(i);

            int[] referencedVertices = face.getVertices();
            int[] referencedNormals = face.getNormalIndices();
            int[] referencedTexcoords = face.getTextureCoordinateIndices();

            Vector3f[] tangentBitangent = null;
            if(USE_PRECOMPUTED_TANGENTSPACE) {
                boolean hasTexcoords = referencedTexcoords[0] != -1;
                if(hasTexcoords) {
                    tangentBitangent = calculateTangentBitangent(verticesTemp.get(referencedVertices[0]-1), verticesTemp.get(referencedVertices[1]-1), verticesTemp.get(referencedVertices[2]-1),
                            texcoordsTemp.get(referencedTexcoords[0]-1), texcoordsTemp.get(referencedTexcoords[1]-1), texcoordsTemp.get(referencedTexcoords[2]-1),
                            normalsTemp.get(referencedNormals[0]-1), normalsTemp.get(referencedNormals[1]-1), normalsTemp.get(referencedNormals[2]-1));
                } else {
                    tangentBitangent = new Vector3f[6];
                    Vector3f edge1 = Vector3f.sub(verticesTemp.get(referencedVertices[1]-1), verticesTemp.get(referencedVertices[0]-1), null);
                    Vector3f edge2 = Vector3f.sub(verticesTemp.get(referencedVertices[2]-1), verticesTemp.get(referencedVertices[0]-1), null);
                    tangentBitangent[0] = edge1;
                    tangentBitangent[1] = edge2;
                    tangentBitangent[2] = edge1;
                    tangentBitangent[3] = edge2;
                    tangentBitangent[4] = edge1;
                    tangentBitangent[5] = edge2;
                }
            }

            for (int j = 0; j < 3; j++) {
                Vector3f referencedVertex = verticesTemp.get(referencedVertices[j]-1);
                Vector2f referencedTexcoord = new Vector2f(0,0);
                try {
                    referencedTexcoord = texcoordsTemp.get(referencedTexcoords[j]-1);
                } catch (Exception e) {

                }
                Vector3f referencedNormal = normalsTemp.get(referencedNormals[j]-1);

                values.add(referencedVertex.x);
                values.add(referencedVertex.y);
                values.add(referencedVertex.z);
                values.add(referencedTexcoord.x);
                values.add(referencedTexcoord.y);
                values.add(referencedNormal.x);
                values.add(referencedNormal.y);
                values.add(referencedNormal.z);

                if(USE_PRECOMPUTED_TANGENTSPACE) {
                    values.add(tangentBitangent[2*j].x);
                    values.add(tangentBitangent[2*j].y);
                    values.add(tangentBitangent[2*j].z);
                    values.add(tangentBitangent[2*j+1].x);
                    values.add(tangentBitangent[2*j+1].y);
                    values.add(tangentBitangent[2*j+1].z);
                }
            }

        }

        List<Integer> indexBuffer = new ArrayList<>();
        for(int i = 0; i < values.size(); i+=valuesPerVertex) {
            Float[] vertexValues = new Float[valuesPerVertex];
            for(int arrayIndex = 0; arrayIndex < valuesPerVertex; arrayIndex++) {
                vertexValues[arrayIndex] = values.get(i+arrayIndex);
            }

            boolean containsVertex = false;
            int indexOfAlreadyContainedVertex = -1;
            for(int z = 0; z < vertexBuffer.size(); z++) {
                Float[] currentVertex = vertexBuffer.get(z);
                boolean currentVertexEquals = Arrays.equals(vertexValues, currentVertex);
                if(currentVertexEquals) {
                    containsVertex = true;
                    indexOfAlreadyContainedVertex = z;
                    break;
                }
            }
            if(containsVertex) {
                indexBuffer.add(indexOfAlreadyContainedVertex);
            } else {
                vertexBuffer.add(vertexValues);
                indexBuffer.add(vertexBuffer.size()-1);
            }
        }

        System.out.println("Total vertices count: " + (values.size()/valuesPerVertex));
        System.out.println("Different vertices count: " + vertexBuffer.size());
        System.out.println("Index count: " + indexBuffer.size());

        floatArray = new float[vertexBuffer.size()*valuesPerVertex];
        for (int i = 0; i < vertexBuffer.size(); i++) {
            Float[] floats = vertexBuffer.get(i);
            for(int floatIndex = 0; floatIndex < valuesPerVertex; floatIndex++) {
                floatArray[i*valuesPerVertex+floatIndex] = floats[floatIndex];
            }
        }
        indices = new int[indexBuffer.size()];
        for (int i = 0; i < indexBuffer.size(); i++) {
            indices[i] = indexBuffer.get(i);
        }


        /////////

        if(facesTemp.size() > 4) {
            int sizeBeforeReduction = facesTemp.size();
//            indices = reduceMesh(Collections.unmodifiableList(verticesTemp), Collections.unmodifiableList(facesTemp), floatArray, indices, valuesPerVertex);
//            System.out.println("############## faces count before reduction: " + sizeBeforeReduction);
//            System.out.println("############## faces count after reduction: " + facesTemp.size());
        }

        /////////

    }


    private List<VertexInfo> collapseCostSet = new ArrayList<>();
    private int[] reduceMesh(List<Vector3f> verticesSource, List<Face> facesSource, float[] values, int[] indices, int valuesPerVertex) {

        verticesSource = new ArrayList<>();
        facesSource = new ArrayList<>();
        for(int i = 0; i < values.length; i+=valuesPerVertex) {
            verticesSource.add(new Vector3f(values[i], values[i+1], values[i+2]));
        }
        for(int i = 0; i < indices.length; i+=3) {
            facesSource.add(new Face(Arrays.copyOfRange(indices, i, i+3)));
        }

        ArrayList<Face> result = new ArrayList<>();
        vertexList = new ArrayList<>();

        for(int i = 0; i < verticesSource.size(); i++) {
            Vector3f vertex = verticesSource.get(i);
            vertexList.add(new VertexInfo(vertex, i));
        }

        for(int i = 0; i < facesSource.size(); i++) {
            Face face = facesSource.get(i);
            for(int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                int vertexListIndex = face.getVertices()[vertexIndex];
                VertexInfo vertex = vertexList.get(vertexListIndex);
//                vertex.addEdgeIfAbsent(new Edge(vertex));
                vertex.faces.add(face);

                for (int n = 0; n < 3; n++) {
                    if (vertexIndex != n) {
                        VertexInfo vertexN = vertexList.get(face.getVertex(n));
                        addEdge(vertex, new Edge(vertexN));
//                        vertex.addEdgeIfAbsent(new Edge(vertexN));
//                        vertexN.addEdgeIfAbsent(new Edge(vertex));
                    }
                }
            }
        }


        System.out.println("############## Computing vertex collapse costs");
        for(VertexInfo vertexInfo : vertexList) {
            computeVertexCollapseCost(verticesSource, vertexInfo);
        }

        System.out.println("############## Finished calculating vertex collapse costs");

        int triCount = facesSource.size();
        int neededTriCount = (int) (facesSource.size() - (facesSource.size() * (0.5f)));
        while (neededTriCount < triCount) {
            Collections.sort(collapseCostSet, collapseComparator);
            Iterator<VertexInfo> it = collapseCostSet.iterator();

            if (it.hasNext()) {
                VertexInfo v = it.next();
                if (v.collapseCost < VertexInfo.NEVER_COLLAPSE_COST) {
                    if (!collapse(v, verticesSource)) {
                        System.out.println(String.format("Couldn't collapse vertex{0}", v.index));
                    }
                    Iterator<VertexInfo> it2 = collapseCostSet.iterator();
                    if (it2.hasNext()) {
                        it2.next();
                        it2.remove();// Remove src from collapse costs.
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
            triCount = facesSource.size() - nbCollapsedTri;
        }

        for(VertexInfo vertexInfo : collapseCostSet) {
            for(Face face : vertexInfo.faces) {
                if(!result.contains(face) && !face.isRemoved) {
                    result.add(face);
                }
            }
        }

//        return result;
        int[] resultIndices = new int[result.size() * 3];
        for(int i = 0; i < result.size(); i++) {
            Face currentFace = result.get(i);
            resultIndices[i] = currentFace.getVertex(0);
            resultIndices[i+1] = currentFace.getVertex(1);
            resultIndices[i+2] = currentFace.getVertex(2);
        }
        return resultIndices;
    }
    private Comparator collapseComparator = new Comparator<VertexInfo>() {
        public int compare(VertexInfo o1, VertexInfo o2) {
            if (Float.compare(o1.collapseCost, o2.collapseCost) == 0) {
                return 0;
            }
            if (o1.collapseCost < o2.collapseCost) {
                return -1;
            }
            return 1;
        }
    };

    private boolean collapse(VertexInfo src, List<Vector3f> verticesSource) {
        int dest = src.collapseTo;
        if (src.edges.isEmpty()) {
            return false;
        }
        List<CollapsedEdge> tmpCollapsedEdges = new ArrayList<>();
        {
            Iterator<Face> it = src.faces.iterator();
            while(it.hasNext()) {
                Face triangle = it.next();
                if (triangle.hasVertex(dest)) {
                    int srcID = triangle.indexOf(src.index);
                    if (!hasSrcID(srcID, tmpCollapsedEdges)) {
                        CollapsedEdge cEdge = new CollapsedEdge();
                        cEdge.srcID = srcID;
                        cEdge.dstID = triangle.indexOf(dest);
                        tmpCollapsedEdges.add(cEdge);
                    }
//                indexCount -= 3;
// 3. task
                    triangle.isRemoved = true;
                    nbCollapsedTri++;
                    removeTriangleFromEdges(triangle, src);
                    it.remove();
                }
            }
        }
        {
            Iterator<Face> it = src.faces.iterator();
            while (it.hasNext()) {
                Face triangle = it.next();
                if (!triangle.hasVertex(dest)) {

                    int srcID = src.index;
                    int indexInCollapsedEdges = findDstID(srcID, tmpCollapsedEdges);
                    if (indexInCollapsedEdges == Integer.MAX_VALUE) {
                        triangle.isRemoved = true;
//                    indexCount -= 3;
                        removeTriangleFromEdges(triangle, src);
                        it.remove();
                        nbCollapsedTri++;
                        continue;
                    }
                    int dstID = tmpCollapsedEdges.get(indexInCollapsedEdges).dstID;
                    replaceVertexID(triangle, srcID, dstID, vertexList.get(dest - 1));
//                if (bestQuality) {
//                    triangle.computeNormal();
//                }
                }
            }
        }
//        if (bestQuality) {
//            for (Edge edge : src.edges) {
//                updateVertexCollapseCost(edge.destination);
//            }
//            updateVertexCollapseCost(dest);
//            for (Edge edge : dest.edges) {
//                updateVertexCollapseCost(edge.destination);
//            }
//        } else
        {
            SortedSet<VertexInfo> updatable = new TreeSet<>(collapseComparator);
            for (Edge edge : src.edges) {
                updatable.add(edge.destination);
                for (Edge edge1 : edge.destination.edges) {
                    updatable.add(edge1.destination);
                }
            }
            for (VertexInfo vertex : updatable) {
                updateVertexCollapseCost(vertex, verticesSource);
            }
        }
        return true;
    }

    private void updateVertexCollapseCost(VertexInfo vertex, List<Vector3f> verticesSource) {
        float collapseCost = VertexInfo.UNINITIALIZED_COLLAPSE_COST;
        VertexInfo collapseTo = null;

        for (Edge edge : vertex.edges) {
            edge.collapseCost = computeEdgeCollapseCost(vertex, edge, verticesSource);
            //  assert (edge.collapseCost != UNINITIALIZED_COLLAPSE_COST);
            if (collapseCost > edge.collapseCost) {
                collapseCost = edge.collapseCost;
                collapseTo = edge.destination;
            }
        }
        if (collapseCost != vertex.collapseCost || vertex.collapseTo != collapseTo.index) {
//            assert (vertex.collapseTo != null);
//            assert (find(collapseCostSet, vertex));
            collapseCostSet.remove(vertex);
            if (collapseCost != VertexInfo.UNINITIALIZED_COLLAPSE_COST) {
                vertex.collapseCost = collapseCost;
//                System.out.println("updated collapse cost to " + vertex.collapseCost);
                vertex.collapseTo = collapseTo.index;
                collapseCostSet.add(vertex);
            }
        }
        //  assert (vertex.collapseCost != UNINITIALIZED_COLLAPSE_COST);
    }

    private void replaceVertexID(Face triangle, int oldID, int newID, VertexInfo dst) {
        dst.faces.add(triangle);
        // NOTE: triangle is not removed from src. This is implementation specific optimization.

        // Its up to the compiler to unroll everything.
        for (int i = 0; i < 3; i++) {
            if (triangle.getVertex(i) == oldID) {
                for (int n = 0; n < 3; n++) {
                    if (i != n) {
                        // This is implementation specific optimization to remove following line.
                        //removeEdge(triangle.vertex[i], new Edge(triangle.vertex[n]));

                        VertexInfo vertexN = vertexList.get(triangle.getVertex(n));
                        VertexInfo vertexI = vertexList.get(triangle.getVertex(i));
                        removeEdge(vertexN, new Edge(vertexI));
                        addEdge(vertexN, new Edge(dst));
                        addEdge(dst, new Edge(vertexN));
                    }
                }
//                dst.index = newID;
                triangle.getVertices()[i] = newID;//dst.index;
                return;
            }
        }
        //   assert (false);
    }

    private void addEdge(VertexInfo v, Edge edge) {
        //  assert (edge.destination != v);

        for (Edge ed : v.edges) {
            if (ed.equals(edge)) {
                ed.refCount++;
                return;
            }
        }

        v.edges.add(edge);
        edge.refCount = 1;

    }

    private int findDstID(int srcId, List<CollapsedEdge> tmpCollapsedEdges) {
        int i = 0;
        for (CollapsedEdge collapsedEdge : tmpCollapsedEdges) {
            if (collapsedEdge.srcID == srcId) {
                return i;
            }
            i++;
        }
        return Integer.MAX_VALUE;
    }

    private void removeTriangleFromEdges(Face triangle, VertexInfo skip) {
        for (int i = 0; i < 3; i++) {
            if (triangle.getVertex(i) != skip.index) {
                vertexList.get(triangle.getVertex(i)).faces.remove(triangle);
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int n = 0; n < 3; n++) {
                if (i != n) {
                    int vertexI = triangle.getVertex(i);
                    VertexInfo vertexInfoI = vertexList.get(vertexI);
                    int vertexN = triangle.getVertex(n);
                    VertexInfo vertexInfoN = vertexList.get(vertexN);
                    removeEdge(vertexInfoI, new Edge(vertexInfoN));
                }
            }
        }
    }


    private void removeEdge(VertexInfo v, Edge edge) {
        Edge ed = null;
        for (Edge edge1 : v.edges) {
            if (edge1.equals(edge)) {
                ed = edge1;
                break;
            }
        }

        if(ed != null) {
            if (ed.refCount == 1) {
                v.edges.remove(ed);
            } else {
                ed.refCount--;
            }
        }

    }

    private class CollapsedEdge {
        int srcID;
        int dstID;
    }

    private boolean hasSrcID(int srcID, List<CollapsedEdge> cEdges) {
        for (CollapsedEdge collapsedEdge : cEdges) {
            if (collapsedEdge.srcID == srcID) {
                return true;
            }
        }
        return false; // Not found
    }
    private void computeVertexCollapseCost(List<Vector3f> verticesSource, VertexInfo vertex) {

        vertex.collapseCost = VertexInfo.UNINITIALIZED_COLLAPSE_COST;
        //  assert (!vertex.edges.isEmpty());
        for (Edge edge : vertex.edges) {
            edge.collapseCost = computeEdgeCollapseCost(vertex, edge, verticesSource);
//           assert (edge.collapseCost != VertexInfo.UNINITIALIZED_COLLAPSE_COST);
            if (vertex.collapseCost > edge.collapseCost) {
                vertex.collapseCost = edge.collapseCost;
//                System.out.println("set collapse cost to " + vertex.collapseCost);
                vertex.collapseTo = edge.destination.index;
            }
        }
        // assert (vertex.collapseCost != UNINITIALIZED_COLLAPSE_COST);
        collapseCostSet.add(vertex);
    }

    private float computeEdgeCollapseCost(VertexInfo src, Edge dstEdge, List<Vector3f> verticesSource) {
        VertexInfo dest = dstEdge.destination;
        if (src.faces.size() == 1 || dest.faces.size() == 1) {
            return VertexInfo.NEVER_COLLAPSE_COST;
        }

        for(Face face : src.faces) {
            if(!face.hasVertex(dest.index)) {
                int pv0, pv1, pv2;

                pv0 = face.getVertex(0) == src.index ? dest.index : face.getVertex(0);
                pv1 = face.getVertex(1) == src.index ? dest.index : face.getVertex(1);
                pv2 = face.getVertex(2) == src.index ? dest.index : face.getVertex(2);

                Vector3f tmpV1 = Vector3f.sub(verticesSource.get(pv1), verticesSource.get(pv0), null);
                Vector3f tmpV2 = Vector3f.sub(verticesSource.get(pv2), verticesSource.get(pv1), null);

                Vector3f newNormal = Vector3f.cross(tmpV1, tmpV2, null).normalise(null);

                float dotProd = Vector3f.dot(newNormal, calculateFaceNormal(vertexList.get(pv0),
                                                            vertexList.get(pv1),vertexList.get(pv2)));
                if(dotProd < 0.0f) {
                    return VertexInfo.NEVER_COLLAPSE_COST;
                }
            }
        }

        float cost;

        // Special cases
        // If we're looking at a border vertex
        if (isBorderVertex(src)) {
            if (dstEdge.refCount > 1) {
                // src is on a border, but the src-dest edge has more than one tri on it
                // So it must be collapsing inwards
                // Mark as very high-value cost
                // curvature = 1.0f;
                cost = 1.0f;
            } else {
                // Collapsing ALONG a border
                // We can't use curvature to measure the effect on the model
                // Instead, see what effect it has on 'pulling' the other border edges
                // The more colinear, the less effect it will have
                // So measure the 'kinkiness' (for want of a better term)

                // Find the only triangle using this edge.
                // PMTriangle* triangle = findSideTriangle(src, dst);

                cost = 0.0f;
                Vector3f collapseEdge = Vector3f.sub(src.position, dest.position, null);
                collapseEdge = collapseEdge.normalise(null);

                for (Edge edge : src.edges) {

                    VertexInfo neighbor = edge.destination;
                    //reference check intended
                    if (neighbor != dest && edge.refCount == 1) {
                        Vector3f otherBorderEdge = Vector3f.sub(src.position, neighbor.position, null);
                        otherBorderEdge = otherBorderEdge.normalise(null);
                        // This time, the nearer the dot is to -1, the better, because that means
                        // the edges are opposite each other, therefore less kinkiness
                        // Scale into [0..1]
                        float kinkiness = (Vector3f.dot(otherBorderEdge, collapseEdge) + 1.002f) * 0.5f;
                        cost = Math.max(cost, kinkiness);
                    }
                }
            }
        } else { // not a border

            // Standard inner vertex
            // Calculate curvature
            // use the triangle facing most away from the sides
            // to determine our curvature term
            // Iterate over src's faces again
            cost = 0.001f;

            for (Face triangle : src.faces) {
                float mincurv = 1.0f; // curve for face i and closer side to it

                for (Face triangle2 : src.faces) {
                    if (triangle2.hasVertex(dest.index)) {

                        // Dot product of face normal gives a good delta angle
                        Vector3f faceNormalOne = Face.calculateFaceNormal(vertexList.get(triangle.getVertex(0)).position,
                                vertexList.get(triangle.getVertex(1)).position,
                                vertexList.get(triangle.getVertex(2)).position);
                        Vector3f faceNormalTwo = Face.calculateFaceNormal(vertexList.get(triangle2.getVertex(0)).position,
                                vertexList.get(triangle2.getVertex(1)).position,
                                vertexList.get(triangle2.getVertex(2)).position);
                        float dotprod = Vector3f.dot(faceNormalOne, faceNormalTwo);

                        // NB we do (1-..) to invert curvature where 1 is high curvature [0..1]
                        // Whilst dot product is high when angle difference is low
                        mincurv = Math.min(mincurv, (1.002f - dotprod) * 0.5f);
                    }
                }
                cost = Math.max(cost, mincurv);
            }
        }

        // check for texture seam ripping
//        if (src.isSeam) {
//            if (!dest.isSeam) {
//                cost += meshBoundingSphereRadius;
//            } else {
                cost += 1 * 0.5;
//            }
//        }

        //   assert (cost >= 0);

//        System.out.println("edgeCost is " + cost);
        float lengthSquared = Vector3f.sub(src.position, dest.position, null).lengthSquared();
        if(lengthSquared == 0) {
            throw new IllegalStateException("Wrong VertexInfo src and dest!");
        }
        return cost * lengthSquared;
    }

    boolean isBorderVertex(VertexInfo vertex) {
        for (Edge edge : vertex.edges) {
            if (edge.refCount == 1) {
                return true;
            }
        }
        return false;
    }

    public static Vector3f calculateFaceNormal(VertexInfo a, VertexInfo b, VertexInfo c) {
        Vector3f tmpV1 = Vector3f.sub(b.position, a.position, null);
        Vector3f tmpV2 = Vector3f.sub(c.position, b.position, null);

        return Vector3f.cross(tmpV1.normalise(null), tmpV2.normalise(null), null).normalise(null);
    }

    private boolean contains(int[] vertexIndices, int vertexIndexOfFaceA) {
        for(int i = 0; i < 3; i++) {
            if(vertexIndices[i] == vertexIndexOfFaceA) { return true; }
        }
        return false;
    }

    private Vector3f[] calculateTangentBitangent(Vector3f v1, Vector3f v2, Vector3f v3, Vector2f w1, Vector2f w2, Vector2f w3, Vector3f n1, Vector3f n2, Vector3f n3) {
        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();

        Vector3f edge1 = Vector3f.sub(v2, v1, null);
        Vector3f edge2 = Vector3f.sub(v3, v1, null);

        Vector2f deltaUV1 = Vector2f.sub(w2, w1, null);
        Vector2f deltaUV2 = Vector2f.sub(w3, w1, null);

        float DeltaU1 = deltaUV1.x;
        float DeltaV1 = deltaUV1.y;
        float DeltaU2 = deltaUV2.x;
        float DeltaV2 = deltaUV2.y;

        float f = 1.0F / (deltaUV1.x * deltaUV2.y - deltaUV1.y * deltaUV2.x);

        tangent = Vector3f.sub((Vector3f) new Vector3f(edge1).scale(DeltaV2), (Vector3f) new Vector3f(edge2).scale(DeltaV1), null);
        bitangent = Vector3f.sub((Vector3f) new Vector3f(edge2).scale(DeltaU1), (Vector3f) new Vector3f(edge1).scale(DeltaU2), null);

        tangent = (Vector3f) new Vector3f(tangent).scale(f);
        bitangent = (Vector3f) new Vector3f(bitangent).scale(f);

        Vector3f tangent1 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n1).scale(Vector3f.dot(n1, tangent))), null).normalise(null);
        Vector3f tangent2 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n2).scale(Vector3f.dot(n2, tangent))), null).normalise(null);
        Vector3f tangent3 = Vector3f.sub(tangent, (Vector3f)(new Vector3f(n3).scale(Vector3f.dot(n3, tangent))), null).normalise(null);

        if (Vector3f.dot(Vector3f.cross(n1, tangent1, null), bitangent) < 0.0f){
            tangent1.scale(-1.0f);
        }
        if (Vector3f.dot(Vector3f.cross(n2, tangent2, null), bitangent) < 0.0f){
            tangent2.scale(-1.0f);
        }
        if (Vector3f.dot(Vector3f.cross(n3, tangent3, null), bitangent) < 0.0f){
            tangent3.scale(-1.0f);
        }

        Vector3f[] result = new Vector3f[2*3];
        result[0] = tangent1;
        result[1] = bitangent;
        result[2] = tangent2;
        result[3] = bitangent;
        result[4] = tangent3;
        result[5] = bitangent;
        return result;
    }

    public void createVertexBuffer() {

        FloatBuffer verticesFloatBuffer = BufferUtils.createFloatBuffer(floatArray.length);
        verticesFloatBuffer.rewind();
        verticesFloatBuffer.put(floatArray);
        verticesFloatBuffer.rewind();

        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
        indexBuffer.rewind();
        indexBuffer.put(indices);
        indexBuffer.rewind();

        int[] dst = new int[indexBuffer.capacity()];
        indexBuffer.get(dst);
//        System.out.println(Arrays.toString(dst));

        vertexBuffer = new VertexBuffer(verticesFloatBuffer, DEFAULTCHANNELS, indexBuffer);
        vertexBuffer.upload();
    }

    @Override
    public String getIdentifier() {
        return "ModelComponent";
    }

    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public Vector4f[] getMinMax() {
        return model.getMinMax();
    }

    public Vector3f getCenter() {
        return model.getCenter();
    }
}
