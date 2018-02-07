package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.model.loader.md5.AnimCompiledVertex;
import de.hanno.hpengine.engine.model.loader.md5.AnimatedModel;
import de.hanno.hpengine.engine.model.loader.md5.MD5Loader;
import de.hanno.hpengine.engine.model.loader.md5.MD5Mesh;
import de.hanno.hpengine.engine.scene.AnimatedVertex;
import de.hanno.hpengine.engine.scene.Vertex;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class AnimatedModelTest extends TestWithEngine {

    @Test
    public void testBufferValues() {
        LoadModelCommand command = new LoadModelCommand(new File("C:\\workspace\\hpengine/hp/assets/models/bob_lamp_update/bob_lamp_update.md5mesh"), "bob");
        LoadModelCommand.EntityListResult result = command.execute(engine);
        ModelComponent component = result.entities.get(0).getComponent(ModelComponent.class, ModelComponent.class.getSimpleName());
        AnimatedModel animatedModel = (AnimatedModel) component.getModel();

//        Entity entity = EntityManager.getInstance().getEntity("bla", animatedModel);
//        Engine.getInstance().getSceneManager().getScene().add(entity);


        for(int i = 0; i < animatedModel.getMeshes().size(); i++) {
            MD5Mesh mesh = (MD5Mesh) (animatedModel.getMeshes().get(i));
            float[] vertexBufferValues = mesh.getVertexBufferValuesArray();

            for(int vertexIndex = 0; vertexIndex < mesh.getCompiledVertices().size(); vertexIndex++) {
                AnimatedVertex vertex = mesh.getCompiledVertices().get(vertexIndex);
                Assert.assertEquals(vertex.getPosition().x, vertexBufferValues[vertexIndex*11], 0.00001f);
                Assert.assertEquals(vertex.getPosition().y, vertexBufferValues[vertexIndex*11+1], 0.00001f);
                Assert.assertEquals(vertex.getPosition().z, vertexBufferValues[vertexIndex*11+2], 0.00001f);

            }
        }

        float[] vertexBufferValues = animatedModel.getVertexBufferValuesArray();
        int vertexCounter = 0;
        for(int i = 0; i < animatedModel.getMeshes().size(); i++) {
            MD5Mesh mesh = (MD5Mesh) (animatedModel.getMeshes().get(i));
            for(int vertexIndex = 0; vertexIndex < mesh.getCompiledVertices().size(); vertexIndex++) {
                AnimatedVertex vertex = mesh.getCompiledVertices().get(vertexIndex);
                int adjustedVertexIndex = vertexCounter + vertexIndex;
                int floatBufferStartVertex = 11 * adjustedVertexIndex;
                Assert.assertEquals(vertex.getPosition().x, vertexBufferValues[floatBufferStartVertex], 0.0001f);
                Assert.assertEquals(vertex.getPosition().y, vertexBufferValues[floatBufferStartVertex + 1], 0.0001f);
                Assert.assertEquals(vertex.getPosition().z, vertexBufferValues[floatBufferStartVertex + 2], 0.0001f);
                Assert.assertEquals(vertex.getTexCoord().x, vertexBufferValues[floatBufferStartVertex + 3], 0.0001f);
                Assert.assertEquals(vertex.getTexCoord().y, vertexBufferValues[floatBufferStartVertex + 4], 0.0001f);
                Assert.assertEquals(vertex.getNormal().x, vertexBufferValues[floatBufferStartVertex + 5], 0.0001f);
                Assert.assertEquals(vertex.getNormal().y, vertexBufferValues[floatBufferStartVertex + 6], 0.0001f);
                Assert.assertEquals(vertex.getNormal().z, vertexBufferValues[floatBufferStartVertex + 7], 0.0001f);

            }
            vertexCounter += mesh.getCompiledVertices().size();
        }

    }
}
