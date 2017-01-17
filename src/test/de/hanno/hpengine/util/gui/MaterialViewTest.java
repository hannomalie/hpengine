package de.hanno.hpengine.util.gui;

import de.hanno.hpengine.renderer.material.MaterialFactory;
import org.junit.Ignore;
import org.junit.Test;

public class MaterialViewTest extends ViewTest {

    @Ignore
    @Test
    public void showMaterialView() {
        openViewInFrame(new MaterialView(MaterialFactory.getInstance().getDefaultMaterial()));
    }

}
