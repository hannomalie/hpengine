import de.hanno.hpengine.engine.lifecycle.LifeCycle;

public class Init implements LifeCycle {

    private boolean initialized;

    @Override
    public void init() {

        try {
//            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(Engine.WORKDIR_NAME + "/assets/models/bpcem_playground.obj"), "cornellbox").execute(Engine.getInstance());
//            System.out.println("loaded entities : " + loaded.entities.size());
//            Engine.getInstance().getScene().addAll(loaded.entities);
//            Thread.sleep(500);
//            Engine.getEventBus().post(new EntityAddedEvent());
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
