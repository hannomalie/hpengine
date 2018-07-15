package de.hanno.hpengine.engine.model.loader.md5;

import de.hanno.hpengine.engine.BufferableMatrix4f;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo;
import de.hanno.hpengine.engine.model.texture.Texture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MD5Loader {


    /**
     * Constructs and AnimatedModel instace based on a MD5 StaticModel an MD5 Animation
     *
     *
     * @param materialManager
     * @param md5Model The MD5 StaticModel
     * @param animModel The MD5 Animation
     * @return
     * @throws Exception
     */
    public static AnimatedModel process(MaterialManager materialManager, MD5Model md5Model, MD5AnimModel animModel) throws Exception {
        List<Matrix4f> invJointMatrices = calcInJointMatrices(md5Model);
        List<AnimatedFrame> animatedFrames = processAnimationFrames(md5Model, animModel, invJointMatrices);

        List<MD5Mesh> list = new ArrayList<>();
        for (MD5Mesh md5Mesh : md5Model.getMeshes()) {
            MD5Mesh mesh = generateMesh(md5Model, md5Mesh);
            handleTexture(materialManager, mesh, md5Mesh);
            list.add(mesh);
        }

        MD5Mesh[] meshes = new MD5Mesh[list.size()];
        meshes = list.toArray(meshes);

        AnimatedModel result = new AnimatedModel(meshes, animatedFrames, animModel.getBoundInfo(), animModel.getHeader(), invJointMatrices, md5Model.isInvertTexCoordsY());
        return result;
    }

    private static List<Matrix4f> calcInJointMatrices(MD5Model md5Model) {
        List<Matrix4f> result = new ArrayList<>();

        List<MD5JointInfo.MD5JointData> joints = md5Model.getJointInfo().getJoints();
        for (MD5JointInfo.MD5JointData joint : joints) {
            // Calculate translation matrix using joint position
            // Calculates rotation matrix using joint orientation
            // Gets transformation matrix bu multiplying translation matrix by rotation matrix
            // Instead of multiplying we can apply rotation which is optimized internally
            Matrix4f mat = new Matrix4f()
                    .translate(joint.getPosition())
                    .rotate(joint.getOrientation())
                    .invert();
            result.add(mat);
        }
        return result;
    }

    private static MD5Mesh generateMesh(MD5Model md5Model, MD5Mesh md5Mesh) {
        List<AnimCompiledVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        List<MD5Mesh.MD5Vertex> md5Vertices = md5Mesh.getVertices();
        List<MD5Mesh.MD5Weight> weights = md5Mesh.getWeights();
        List<MD5JointInfo.MD5JointData> joints = md5Model.getJointInfo().getJoints();

        for (MD5Mesh.MD5Vertex md5Vertex : md5Vertices) {
            AnimCompiledVertex vertex = new AnimCompiledVertex();
            vertices.add(vertex);

            vertex.position = new Vector3f();
            vertex.textCoords = md5Vertex.getTextCoords();

            int startWeight = md5Vertex.getStartWeight();
            int numWeights = md5Vertex.getWeightCount();

            vertex.jointIndices = new int[numWeights];
            Arrays.fill(vertex.jointIndices, -1);
            vertex.weights = new float[numWeights];
            Arrays.fill(vertex.weights, -1);
            for (int i = startWeight; i < startWeight + numWeights; i++) {
                MD5Mesh.MD5Weight weight = weights.get(i);
                MD5JointInfo.MD5JointData joint = joints.get(weight.getJointIndex());
                Vector3f rotatedPos = new Vector3f(weight.getPosition()).rotate(joint.getOrientation());
                Vector3f acumPos = new Vector3f(joint.getPosition()).add(rotatedPos);
                acumPos.mul(weight.getBias());
                vertex.position.add(acumPos);
                vertex.jointIndices[i - startWeight] = weight.getJointIndex();
                vertex.weights[i - startWeight] = weight.getBias();
            }
        }

        for (MD5Mesh.MD5Triangle tri : md5Mesh.getTriangles()) {
            indices.add(tri.getVertex0());
            indices.add(tri.getVertex1());
            indices.add(tri.getVertex2());

            // Normals
            AnimCompiledVertex v0 = vertices.get(tri.getVertex0());
            AnimCompiledVertex v1 = vertices.get(tri.getVertex1());
            AnimCompiledVertex v2 = vertices.get(tri.getVertex2());
            Vector3f pos0 = v0.position;
            Vector3f pos1 = v1.position;
            Vector3f pos2 = v2.position;

            Vector3f normal = (new Vector3f(pos2).sub(pos0)).cross(new Vector3f(pos1).sub(pos0));

            v0.normal.add(normal);
            v1.normal.add(normal);
            v2.normal.add(normal);
        }

        // Once the contributions have been added, normalize the result
        for(AnimCompiledVertex v : vertices) {
            v.normal.normalize();
        }

        MD5Mesh mesh = createMesh(vertices, indices);
        mesh.setName(md5Mesh.getName());
        return mesh;
    }

    private static List<AnimatedFrame> processAnimationFrames(MD5Model md5Model, MD5AnimModel animModel, List<Matrix4f> invJointMatrices) {
        List<AnimatedFrame> animatedFrames = new ArrayList<>();
        List<MD5Frame> frames = animModel.getFrames();
        for (MD5Frame frame : frames) {
            AnimatedFrame data = processAnimationFrame(md5Model, animModel, frame, invJointMatrices);
            animatedFrames.add(data);
        }
        return animatedFrames;
    }

    private static AnimatedFrame processAnimationFrame(MD5Model md5Model, MD5AnimModel animModel, MD5Frame frame, List<Matrix4f> invJointMatrices) {
        AnimatedFrame result = new AnimatedFrame();

        MD5BaseFrame baseFrame = animModel.getBaseFrame();
        List<MD5Hierarchy.MD5HierarchyData> hierarchyList = animModel.getHierarchy().getHierarchyDataList();

        List<MD5JointInfo.MD5JointData> joints = md5Model.getJointInfo().getJoints();
        int numJoints = joints.size();
        float[] frameData = frame.getFrameData();
        for (int i = 0; i < numJoints; i++) {
            MD5JointInfo.MD5JointData joint = joints.get(i);
            MD5BaseFrame.MD5BaseFrameData baseFrameData = baseFrame.getFrameDataList().get(i);
            Vector3f position = baseFrameData.getPosition();
            Quaternionf orientation = baseFrameData.getOrientation();

            int flags = hierarchyList.get(i).getFlags();
            int startIndex = hierarchyList.get(i).getStartIndex();

            if ((flags & 1) > 0) {
                position.x = frameData[startIndex++];
            }
            if ((flags & 2) > 0) {
                position.y = frameData[startIndex++];
            }
            if ((flags & 4) > 0) {
                position.z = frameData[startIndex++];
            }
            if ((flags & 8) > 0) {
                orientation.x = frameData[startIndex++];
            }
            if ((flags & 16) > 0) {
                orientation.y = frameData[startIndex++];
            }
            if ((flags & 32) > 0) {
                orientation.z = frameData[startIndex++];
            }
            // Update Quaternion's w component
            orientation = MD5Utils.calculateQuaternion(orientation.x, orientation.y, orientation.z);

            // Calculate translation and rotation matrices for this joint
            Matrix4f translateMat = new Matrix4f().translate(position);
            Matrix4f rotationMat = new Matrix4f().rotate(orientation);
            Matrix4f jointMat = translateMat.mul(rotationMat);

            // Joint position is relative to joint's parent index position. Use parent matrices
            // to transform it to model space
            if (joint.getParentIndex() > -1) {
                Matrix4f parentMatrix = result.getLocalJointMatrices()[joint.getParentIndex()];
                jointMat = new BufferableMatrix4f(parentMatrix).mul(jointMat);
            }

            result.setMatrix(i, new BufferableMatrix4f(jointMat), invJointMatrices.get(i));
        }

        return result;
    }

    private static MD5Mesh createMesh(List<AnimCompiledVertex> vertices, List<Integer> indices) {
        List<Float> positions = new ArrayList<>();
        List<Float> textCoords = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> jointIndices = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        final Vector4f absoluteMaximum = new Vector4f(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
        final Vector4f absoluteMinimum = new Vector4f(-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);
        Vector4f min = new Vector4f(absoluteMaximum);
        Vector4f max = new Vector4f(absoluteMinimum);

        for (AnimCompiledVertex vertex : vertices) {
            positions.add(vertex.position.x);
            positions.add(vertex.position.y);
            positions.add(vertex.position.z);

            textCoords.add(vertex.textCoords.x);
            textCoords.add(vertex.textCoords.y);

            normals.add(vertex.normal.x);
            normals.add(vertex.normal.y);
            normals.add(vertex.normal.z);

            int numWeights = vertex.weights.length;
            for (int i = 0; i < Mesh.MAX_WEIGHTS; i++) {
                if (i < numWeights) {
                    jointIndices.add(vertex.jointIndices[i]);
                    weights.add(vertex.weights[i]);
                } else {
                    jointIndices.add(-1);
                    weights.add(-1.0f);
                }
            }

            min.x = vertex.position.x < min.x ? vertex.position.x : min.x;
            min.y = vertex.position.y < min.y ? vertex.position.y : min.y;
            min.z = vertex.position.z < min.z ? vertex.position.z : min.z;

            max.x = vertex.position.x > max.x ? vertex.position.x : max.x;
            max.y = vertex.position.y > max.y ? vertex.position.y : max.y;
            max.z = vertex.position.z > max.z ? vertex.position.z : max.z;

        }

        Vector4f[] minMax = new Vector4f[]{min, max};
        Vector3f[] minMaxVec3 = new Vector3f[]{new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, max.y, max.z)};
        float[] positionsArr = Utils.listToArray(positions);
        float[] textCoordsArr = Utils.listToArray(textCoords);
        float[] normalsArr = Utils.listToArray(normals);
        int[] indicesArr = Utils.listIntToArray(indices);
        int[] jointIndicesArr = Utils.listIntToArray(jointIndices);
        float[] weightsArr = Utils.listToArray(weights);

        MD5Mesh result = new MD5Mesh(positionsArr, textCoordsArr, normalsArr, indicesArr, jointIndicesArr, weightsArr, vertices);

        return result;
    }

    private static void handleTexture(MaterialManager materialManager, Mesh mesh, MD5Mesh md5Mesh) throws Exception {
        String texturePath = md5Mesh.getDiffuseTexture();
        if (texturePath != null && texturePath.length() > 0) {
            try {

                SimpleMaterialInfo materialInfo = new SimpleMaterialInfo(texturePath);
                materialInfo = materialInfo.put(SimpleMaterial.MAP.DIFFUSE, materialManager.getTextureManager().getTexture(texturePath));

                // Handle normal Maps;
                int pos = texturePath.lastIndexOf(".");
                if (pos > 0) {
                    String basePath = texturePath.substring(0, pos);
                    String extension = texturePath.substring(pos, texturePath.length());
                    String normalMapFileName = basePath + "_local" + extension;
                    if (new File(normalMapFileName).exists()) {
                        Texture normalMap = materialManager.getTextureManager().getTexture(normalMapFileName);
                        materialInfo = materialInfo.put(SimpleMaterial.MAP.NORMAL, normalMap);
                    }
                    String heightMapFileName = basePath + "_h" + extension;
                    if (new File(heightMapFileName).exists()) {
                        Texture heightMap = materialManager.getTextureManager().getTexture(heightMapFileName);
                        materialInfo = materialInfo.put(SimpleMaterial.MAP.HEIGHT, heightMap);
                    }
                    String specularMapFile = basePath + "_s" + extension;
                    if (new File(specularMapFile).exists()) {
                        Texture specularMap = materialManager.getTextureManager().getTexture(specularMapFile);
                        materialInfo = materialInfo.put(SimpleMaterial.MAP.SPECULAR, specularMap);
                    }
                }
                SimpleMaterial material = materialManager.getMaterial(materialInfo);
                mesh.setMaterial(material);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            mesh.setMaterial(materialManager.getDefaultMaterial());
        }
    }
}
