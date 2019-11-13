import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.instancing.ClustersComponent;
import de.hanno.hpengine.engine.model.Cluster;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import de.hanno.hpengine.engine.transform.AnimatedTransformSpatial;
import de.hanno.hpengine.engine.transform.Transform;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import de.hanno.hpengine.engine.lifecycle.EngineConsumer;

public class InitInstancedAnimated implements EngineConsumer {

    private boolean initialized;

    int maxDistance = 475;
    int clusterDistance = 3 * maxDistance;
    Vector3f[] clusterLocations = {new Vector3f(clusterDistance, 0, clusterDistance),
            new Vector3f(clusterDistance, 0, -clusterDistance),
            new Vector3f(-clusterDistance, 0, -clusterDistance),
            new Vector3f(0, 0, 0),
            new Vector3f(-clusterDistance, 0, clusterDistance)};

    @Override
    public void consume(de.hanno.hpengine.engine.Engine engine) {

        try {
            loadLotsOfInstances(engine, "/assets/models/doom3monster/monster.md5mesh", 1, "hellknight");
            Thread.sleep(500);
//            loadLotsOfInstances("/assets/models/cube.obj", 100, "cube");
//            Thread.sleep(500);
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected void loadLotsOfInstances(final Engine engine, String assetPath, final int scale, String name) {
        LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + assetPath), name, engine.getScene().getMaterialManager()).execute();
        System.out.println("loaded entities : " + loaded.entities.size());
        for (final Entity entity : loaded.entities) {
//                File componentScriptFile = new File(engine.getDirectories().getGameDir() + "/scripts/SimpleMoveComponent.java");
//                entity.addComponent(new JavaComponent(new CodeSource(componentScriptFile)));
            List<Cluster> clusters = new ArrayList<Cluster>();
            ClustersComponent clustersComponent = new ClustersComponent(engine, entity);
            for (int clusterIndex = 0; clusterIndex < 5; clusterIndex++) {
                Cluster cluster = new Cluster();
                Random random = new Random();
                int count = 6;
                for (int x = -count; x < count; x++) {
                    for (int y = -count; y < count; y++) {
                        for (int z = -count; z < count; z++) {
                            final Transform trafo = new Transform();
                            trafo.scale(scale);
                            float randomFloat = random.nextFloat() - 0.5f;
                            trafo.rotate(new Vector3f(1, 0, 0), -90);
                            trafo.rotate(new Vector3f(0, 0, 1), (int) (random.nextFloat() * 360f));
                            trafo.setTranslation(new Vector3f().add(new Vector3f(clusterLocations[clusterIndex % clusterLocations.length])).add(new Vector3f(randomFloat * maxDistance * x, 0.001f * randomFloat, randomFloat * maxDistance * z)));

                            final ModelComponent modelComponent = entity.getComponent(ModelComponent.class);
                            List<SimpleMaterial> materials = modelComponent.getMaterials();
                            ClustersComponent.addInstance(entity, cluster, trafo, modelComponent, materials, new AnimationController(120, 24f), new AnimatedTransformSpatial(trafo, modelComponent));
                        }
                    }
                }
                clusters.add(cluster);
                System.out.println("Added " + cluster.size());
            }
            clustersComponent.addClusters(clusters);
            entity.addComponent(clustersComponent);
        }

//        Entity debugCam = new Entity("DebugCam");
//        loaded.entities.add(debugCam.addComponent(new Camera(debugCam)));
        engine.getScene().addAll(loaded.entities);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
