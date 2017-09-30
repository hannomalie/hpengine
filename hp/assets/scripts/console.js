Vector3f = Java.type('org.joml.Vector3f');
Vector4f = Java.type('org.joml.Vector4f');
Quaternion = Java.type('org.joml.Quaternionf');
Transform = Java.type('de.hanno.hpengine.engine.Transform');
Instance = Java.type('de.hanno.hpengine.engine.model.Entity.Instance');
Engine = Java.type('de.hanno.hpengine.engine.Engine');
GraphicsContext = Java.type('de.hanno.hpengine.engine.graphics.renderer.GraphicsContext');
EntityFactory = Java.type('de.hanno.hpengine.engine.model.EntityFactory');
MaterialFactory = Java.type('de.hanno.hpengine.engine.model.material.MaterialFactory');
JavaScriptComponent = Java.type('de.hanno.hpengine.engine.component.JavaScriptComponent');
ModelComponent = Java.type('de.hanno.hpengine.engine.component.ModelComponent');


//print(world.getRenderer().getLightFactory().getDirectionalLight().getCamera().getPosition())

//(world.getScene().getDirectionalLight().setOrientation(new Quaternion(0, 0, 0, 1)))
//print(Engine.getInstance().getScene().getDirectionalLight().getOrientation())


//var trafo = new Transform();
//trafo.setPosition(new Vector3f(15,15,15));
//world.getScene().getEntities().get(0).addInstance(trafo);

var count = 4;
var instances = new java.util.ArrayList();
var random = new java.util.Random();
for(var x = -count; x < count; x++) {
	for(var y = -count; y < count; y++) {
		for(var z = -count; z < count; z++) {
			var trafo = new Transform();
			var randomFloat = random.nextFloat() - 0.5;
			trafo.translateLocal(Engine.getInstance().getScene().getEntities().get(0).getPosition());
			trafo.translateLocal(new Vector3f(randomFloat*15*x,randomFloat*15*y,randomFloat*15*z));
			var modelComponent = Engine.getInstance().getScene().getEntities().get(0).getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
			var instance;
			if(modelComponent != null) {
				instance = new Instance(trafo, MaterialFactory.getInstance().getMaterialsAsList().get((x+y+z+3*count)%10), modelComponent.getAnimationController());
			} else {
				instance = new Instance(trafo, MaterialFactory.getInstance().getMaterialsAsList().get((x+y+z+3*count)%10));
			}
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

