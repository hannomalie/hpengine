import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.transform.Transform;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class Init implements LifeCycle {

    private boolean initialized;

    public void init(Engine engine) {

        try {
            ArrayList<Entity> entities = new ArrayList<Entity>();
            int count = 3;
            Random random = new Random();
            for(int x = -count; x < count; x++) {
                for(int y = -count; y < count; y++) {
                    for(int z = -count; z < count; z++) {
                        Transform trafo = new Transform();
                        float randomFloatX = (random.nextFloat() - 0.5f)* 300*count;
                        float randomFloatY = (random.nextFloat() - 0.5f)* 300*count;
                        float randomFloatZ = (random.nextFloat() - 0.5f)* 300*count;
                        trafo.scale(random.nextFloat()*10.0f);
                        trafo.setTranslation(new Vector3f(randomFloatX, randomFloatY, randomFloatZ));
                        LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"), "cube"+System.currentTimeMillis()).execute(Engine.getInstance());
                        loaded.entities.get(0).set(trafo);
                        entities.add(loaded.entities.get(0));
                    }
                }
            }

            Engine.getInstance().getSceneManager().getScene().addAll(entities);
            Thread.sleep(500);
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public boolean isInitialized() {
        return initialized;
    }
}
