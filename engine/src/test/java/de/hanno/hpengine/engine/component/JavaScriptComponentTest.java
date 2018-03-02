package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.entity.Entity;
import junit.framework.Assert;
import org.junit.Test;

public class JavaScriptComponentTest extends TestWithEngine {

    @Test
    public void globalDefines() {
        engine.getScene().getScriptManager().getGlobalContext().put("myInt", 2);

        Assert.assertEquals(2, engine.getScene().getScriptManager().getGlobalContext().get("myInt"));
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
        engine.getScene().getScriptManager().getGlobalContext().put("myInt", 242);

        Assert.assertEquals(242, engine.getScene().getScriptManager().getGlobalContext().get("myInt"));

        String script = "var myInt = myInt;";
        JavaScriptComponent component = new JavaScriptComponent(script);
        Entity entity = engine.getSceneManager().getScene().getEntityManager().create().addComponent(component);

        Assert.assertEquals(242, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void scriptUpdateFunctionCall() {
        engine.getScene().getScriptManager().getGlobalContext().put("initCalled", false);
        engine.getScene().getScriptManager().getGlobalContext().put("updateCalled", false);

        Assert.assertEquals(false, engine.getScene().getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(false, engine.getScene().getScriptManager().getGlobalContext().get("updateCalled"));

        String script = "var init = function(world) { initCalled = true; };" +
                "var update = function(seconds) { updateCalled = true; };";
        JavaScriptComponent component = new JavaScriptComponent(script);
        Entity entity = engine.getSceneManager().getScene().getEntityManager().create().addComponent(component);

        entity.update(0.1f);

//        try {
//            world.getScriptManager().eval("marker = true;");
//        } catch (ScriptException e) {
//            e.printStackTrace();
//        }

        component.update(0.1f);
        Assert.assertEquals(true, engine.getScene().getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(true, engine.getScene().getScriptManager().getGlobalContext().get("updateCalled"));
    }
}
