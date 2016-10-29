import engine.AppContext;
import engine.lifecycle.LifeCycle;
import event.EntityAddedEvent;
import renderer.command.LoadModelCommand;

import java.io.File;

public class Init implements LifeCycle {

    private boolean initialied;

    @Override
    public void init() {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(AppContext.WORKDIR_NAME + "/assets/models/cornellbox.obj"), "cornellbox").execute(AppContext.getInstance());
            System.out.println("loaded entities : " + loaded.entities.size());
            AppContext.getInstance().getScene().addAll(loaded.entities);
            Thread.sleep(500);
            AppContext.getEventBus().post(new EntityAddedEvent());
            initialied = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean isInitialized() {
        return initialied;
    }
}
