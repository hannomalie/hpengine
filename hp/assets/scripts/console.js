Vector3f = Java.type('org.lwjgl.util.vector.Vector3f');
Vector4f = Java.type('org.lwjgl.util.vector.Vector4f');
Quaternion = Java.type('org.lwjgl.util.vector.Quaternion');
Transform = Java.type('de.hanno.hpengine.engine.Transform');
Engine = Java.type('de.hanno.hpengine.engine.Engine');
Renderer = Java.type('de.hanno.hpengine.renderer.Renderer');
GraphicsContext = Java.type('de.hanno.hpengine.renderer.GraphicsContext');
EntityFactory = Java.type('de.hanno.hpengine.engine.model.EntityFactory');


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
            	instances.add(trafo);
		}
	}
}

print("adding...." + instances.size());
GraphicsContext.getInstance().execute(new java.lang.Runnable() {
	run: function() {
		Engine.getInstance().getScene().getEntities().get(0).addInstances(instances);
	}
});

