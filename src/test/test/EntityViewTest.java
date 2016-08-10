package test;

import engine.AppContext;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import org.junit.Test;
import util.gui.EntityView;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class EntityViewTest extends TestWithAppContext {

    @Test
    public void testEntityViewGui() throws Exception {
        List<Model> models = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sphere.obj"));
        Entity parentEntity = EntityFactory.getInstance().getEntity("parent", models);

        JFrame frame = new JFrame();

        frame.add(new EntityView(AppContext.getInstance(), parentEntity));

        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        while(true) {Thread.sleep(10);}
    }

}
