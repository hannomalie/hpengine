Vector3f = Java.type('org.joml.Vector3f');
Vector4f = Java.type('org.joml.Vector4f');
Quaternion = Java.type('org.joml.Quaternionf');
Transform = Java.type('de.hanno.hpengine.engine.transform.Transform');
Instance = Java.type('de.hanno.hpengine.engine.model.Instance');
Engine = Java.type('de.hanno.hpengine.engine.Engine');
GraphicsContext = Java.type('de.hanno.hpengine.engine.graphics.renderer.GraphicsContext');
EntityFactory = Java.type('de.hanno.hpengine.engine.model.EntityFactory');
MaterialFactory = Java.type('de.hanno.hpengine.engine.model.material.MaterialFactory');
JavaScriptComponent = Java.type('de.hanno.hpengine.engine.component.JavaScriptComponent');
ModelComponent = Java.type('de.hanno.hpengine.engine.component.ModelComponent');


//print(world.getRenderer().getLightFactory().getDirectionalLight().getCamera().getPosition())

//(world.getScene().getDirectionalLight().setOrientation(new Quaternion(0, 0, 0, 1)))
//print(Engine.getInstance().getSceneManager().getScene().getDirectionalLight().getOrientation())


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
			var randomFloat2 = random.nextFloat() - 0.5;
			var randomFloat3 = random.nextFloat() - 0.5;
			trafo.translateLocal(Engine.getInstance().getSceneManager().getScene().getEntities().get(0).getPosition());
			var translationDistanceX = 150
			var translationDistanceY = 0
			var translationDistanceZ = 150
			trafo.translateLocal(new Vector3f(randomFloat*translationDistanceX*x,randomFloat2*translationDistanceY*y,randomFloat3*translationDistanceZ*z));
			trafo.rotateY(randomFloat2*10.0);
			var modelComponent = Engine.getInstance().getSceneManager().getScene().getEntities().get(0).getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
			var instance;
			if(modelComponent != null) {
			    var sourceController = modelComponent.getAnimationController();
			    if(sourceController != null) {
				    instance = new Instance(trafo, MaterialFactory.getInstance().getMaterialsAsList().get((x+y+z+3*count)%10), sourceController.copy(sourceController.getMaxFrames(), sourceController.getFps() + 10*randomFloat));
			    } else {
				    instance = new Instance(trafo, MaterialFactory.getInstance().getMaterialsAsList().get((x+y+z+3*count)%10));
			    }
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
		Engine.getInstance().getSceneManager().getScene().getEntities().get(0).addInstances(instances);
	}
});
print("Has instances now: " + Engine.getInstance().getSceneManager().getScene().getEntities().get(0).getInstances().size())
