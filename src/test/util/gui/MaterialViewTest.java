package util.gui;

import org.junit.Test;
import renderer.material.MaterialFactory;
import test.TestWithAppContext;

import javax.swing.*;

public class MaterialViewTest extends TestWithAppContext {

//    @Ignore
    @Test
    public void showMaterialView() {
        MaterialView materialView = new MaterialView(MaterialFactory.getInstance().getDefaultMaterial());
        JFrame frame = new JFrame();
        frame.getContentPane().add(materialView);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        while(true) {}
    }

}
