package test;

import component.JavaScriptComponent;
import engine.model.Entity;
import engine.model.EntityFactory;
import junit.framework.Assert;
import org.junit.Test;

public class JavaScriptComponentTest extends TestWithAppContext {

    @Test
    public void globalDefines() {
        appContext.getScriptManager().getGlobalContext().put("myInt", 2);

        Assert.assertEquals(2, appContext.getScriptManager().getGlobalContext().get("myInt"));
    }

    @Test
    public void localDefines() {
        JavaScriptComponent component = new JavaScriptComponent();
        Entity entity = EntityFactory.getInstance().getEntity().addComponent(component);
        entity.init();
        component.setInt("myInt", 5);

        Assert.assertEquals(5, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void globalScopeFromLocalScope() {
        appContext.getScriptManager().getGlobalContext().put("myInt", 242);

        Assert.assertEquals(242, appContext.getScriptManager().getGlobalContext().get("myInt"));

        String script = "var myInt = myInt;";
        JavaScriptComponent component = new JavaScriptComponent(script);
        Entity entity = EntityFactory.getInstance().getEntity().addComponent(component);
        entity.init();

        Assert.assertEquals(242, component.getContext().getAttribute("myInt"));
    }

    @Test
    public void scriptUpdateFunctionCall() {
        appContext.getScriptManager().getGlobalContext().put("initCalled", false);
        appContext.getScriptManager().getGlobalContext().put("updateCalled", false);

        Assert.assertEquals(false, appContext.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(false, appContext.getScriptManager().getGlobalContext().get("updateCalled"));

        String script = "var init = function(world) { initCalled = true; };" +
                "var update = function(seconds) { updateCalled = true; };";
        JavaScriptComponent component = new JavaScriptComponent(script);
        Entity entity = EntityFactory.getInstance().getEntity().addComponent(component);
        entity.init();

        entity.update(0.1f);

//        try {
//            world.getScriptManager().eval("marker = true;");
//        } catch (ScriptException e) {
//            e.printStackTrace();
//        }

        component.update(0.1f);
        Assert.assertEquals(true, appContext.getScriptManager().getGlobalContext().get("initCalled"));
        Assert.assertEquals(true, appContext.getScriptManager().getGlobalContext().get("updateCalled"));
    }
}
