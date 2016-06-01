package util.gui;

import org.junit.Ignore;
import org.junit.Test;
import renderer.material.MaterialFactory;

public class MaterialViewTest extends ViewTest {

    @Ignore
    @Test
    public void showMaterialView() {
        openViewInFrame(new MaterialView(MaterialFactory.getInstance().getDefaultMaterial()));
    }

}
