import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.renderer.command.LoadModelCommand;
import java.io.File;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Entity.Instance;
import de.hanno.hpengine.component.JavaComponent;
import de.hanno.hpengine.util.ressources.CodeSource;
import de.hanno.hpengine.engine.Transform;
import java.util.Random;
import java.util.List;
import de.hanno.hpengine.component.ModelComponent;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.renderer.material.MaterialFactory;

public class Init implements LifeCycle {

    private boolean initialized;

    @Override
    public void init() {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(Engine.WORKDIR_NAME + "/assets/models/cube.obj"), "cube").execute(Engine.getInstance());
            System.out.println("loaded entities : " + loaded.entities.size());
            for(Entity current : loaded.entities) {
                current.addComponent(new JavaComponent(new CodeSource(new File(Engine.WORKDIR_NAME + "/assets/scripts/SimpleMoveComponent.java"))));

                List<Instance> instances = new java.util.ArrayList<Instance>();
                Random random = new java.util.Random();
                int count = 10;
                for(int x = -count; x < count; x++) {
                    for(int y = -count; y < count; y++) {
                        for(int z = -count; z < count; z++) {
                            Transform trafo = new Transform();
                            float randomFloat = random.nextFloat() - 0.5f;
                            trafo.setPosition(Vector3f.add(current.getPosition(), new Vector3f(randomFloat*15*x,randomFloat*15*y,randomFloat*15*z), null));
                            //instances.add(new Instance(trafo, current.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMaterial()));
                            instances.add(new Instance(trafo, MaterialFactory.getInstance().getMaterialsAsList().get((x+y+z+3*count)%10)));
                        }
                    }
                }
                current.addInstances(instances);
                System.out.println("Added " + instances.size());
            }

            Engine.getInstance().getScene().addAll(loaded.entities);
            Thread.sleep(500);
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
