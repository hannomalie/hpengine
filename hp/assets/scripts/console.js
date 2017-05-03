Vector3f = Java.type('org.lwjgl.util.vector.Vector3f');
Vector4f = Java.type('org.lwjgl.util.vector.Vector4f');
Quaternion = Java.type('org.lwjgl.util.vector.Quaternion');
Transform = Java.type('de.hanno.hpengine.engine.Transform');
Instance = Java.type('de.hanno.hpengine.engine.model.Entity.Instance');
Engine = Java.type('de.hanno.hpengine.engine.Engine');
GraphicsContext = Java.type('de.hanno.hpengine.renderer.GraphicsContext');
EntityFactory = Java.type('de.hanno.hpengine.engine.model.EntityFactory');
MaterialFactory = Java.type('de.hanno.hpengine.renderer.material.MaterialFactory');
JavaScriptComponent = Java.type('de.hanno.hpengine.component.JavaScriptComponent');


//print(world.getRenderer().getLightFactory().getDirectionalLight().getCamera().getPosition())

//(world.getScene().getDirectionalLight().setOrientation(new Quaternion(0, 0, 0, 1)))
//print(Engine.getInstance().getScene().getDirectionalLight().getOrientation())


//var trafo = new Transform();
//trafo.setPosition(new Vector3f(15,15,15));
//world.getScene().getEntities().get(0).addInstance(trafo);

var count = 10;
var instances = new java.util.ArrayList();
var random = new java.util.Random();
for(var x = -count; x < count; x++) {
	for(var y = -count; y < count; y++) {
		for(var z = -count; z < count; z++) {
			var trafo = new Transform();
			var randomFloat = random.nextFloat() - 0.5;
			trafo.setPosition(Vector3f.add(Engine.getInstance().getScene().getEntities().get(0).getPosition(), new Vector3f(randomFloat*15*x,randomFloat*15*y,randomFloat*15*z), null));
			var instance = new Instance(trafo, MaterialFactory.getInstance().getMaterialsAsList().get((x+y+z+3*count)%11));
            	instances.add(instance);
		}
	}
}

print("adding...." + instances.size());
GraphicsContext.getInstance().execute(new java.lang.Runnable() {
	run: function() {
		Engine.getInstance().getScene().getEntities().get(0).addInstances(instances);
	}
});
var script = new JavaScriptComponent("function update(seconds) {"+
    "for(var instance = 0; instance < entity.getInstances().size(); instance++) {"+
    "entity.getInstances().get(instance).move(new Vector3f(0.001*instance*scriptComponent.get('flip'),0,0.001*instance*scriptComponent.get('flip')));"+
    "}"+
    "entity.move(new Vector3f(0,0,0.1*scriptComponent.get('flip')));"+
    "var counter = scriptComponent.get('counter');"+
    "scriptComponent.put('counter', counter+1);"+
    "if(scriptComponent.get('counter') >= 149) {"+
        "scriptComponent.put('flip', scriptComponent.get('flip')*-1);"+
        "scriptComponent.put('counter', 0);"+
    "}"+
"}")
script.put("flip", 1);
script.put("counter", 0);
Engine.getInstance().getScene().getEntities().get(0).addComponent(script);

