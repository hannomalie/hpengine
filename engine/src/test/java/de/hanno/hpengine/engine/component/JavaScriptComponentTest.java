package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.util.script.ScriptManager;
import junit.framework.Assert;
import org.junit.Test;

public class JavaScriptComponentTest extends TestWithEngine {

    private ScriptManager scriptManager = engine.getManagers().get(ScriptManager.class);

    @Test
    public void globalDefines() {
        scriptManager.getGlobalContext().put("myInt", 2);

        Assert.assertEquals(2, scriptManager.getGlobalContext().get("myInt"));
    }

    @Test
    public void localDefines() {
        JavaScriptComponent component = new JavaScriptComponent();
        Entity entity = engine.getSceneManager().getScene().getEntityManager().create().addComponent(component);
        component.setInt("myInt", 5);

        Assert.assertEquals(5, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void globalScopeFromLocalScope() {
        scriptManager.getGlobalContext().put("myInt", 242);

        Assert.assertEquals(242, scriptManager.getGlobalContext().get("myInt"));

        String script = "var myInt = myInt;";
        JavaScriptComponent component = new JavaScriptComponent(script, scriptManager);
        Entity entity = engine.getSceneManager().getScene().getEntityManager().create().addComponent(component);

        Assert.assertEquals(242, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void scriptUpdateFunctionCall() {
        scriptManager.getGlobalContext().put("initCalled", false);
        scriptManager.getGlobalContext().put("updateCalled", false);

        Assert.assertEquals(false, scriptManager.getGlobalContext().get("initCalled"));
        Assert.assertEquals(false, scriptManager.getGlobalContext().get("updateCalled"));

        String script = "var init = bufferMaterialsExtractor(world) { initCalled = true; };" +
                "var update = bufferMaterialsExtractor(seconds) { updateCalled = true; };";
        JavaScriptComponent component = new JavaScriptComponent(script, scriptManager);
        Entity entity = engine.getSceneManager().getScene().getEntityManager().create().addComponent(component);

        entity.update(0.1f);

//        try {
//            world.getScriptManager().eval("marker = true;");
//        } catch (ScriptException e) {
//            e.printStackTrace();
//        }

        component.update(0.1f);
        Assert.assertEquals(true, scriptManager.getGlobalContext().get("initCalled"));
        Assert.assertEquals(true, scriptManager.getGlobalContext().get("updateCalled"));
    }
}
