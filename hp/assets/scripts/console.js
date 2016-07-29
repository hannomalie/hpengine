Vector3f = Java.type('org.lwjgl.util.vector.Vector3f');
Vector4f = Java.type('org.lwjgl.util.vector.Vector4f');
Quaternion = Java.type('org.lwjgl.util.vector.Quaternion');
Transform = Java.type('engine.Transform');
AppContext = Java.type('engine.AppContext');
Renderer = Java.type('renderer.Renderer');
OpenGLContext = Java.type('renderer.OpenGLContext');
EntityFactory = Java.type('engine.model.EntityFactory');


//print(world.getRenderer().getLightFactory().getDirectionalLight().getCamera().getPosition())

//(world.getScene().getDirectionalLight().setOrientation(new Quaternion(0, 0, 0, 1)))
//print(AppContext.getInstance().getScene().getDirectionalLight().getOrientation())


//var trafo = new Transform();
//trafo.setPosition(new Vector3f(15,15,15));
//world.getScene().getEntities().get(0).addInstance(trafo);
//EntityFactory.getInstance().bufferEntities();

var count = 13;
var instances = new java.util.ArrayList();
for(var x = 0; x < count; x++) {
	for(var y = 0; y < count; y++) {
		for(var z = 0; z < count; z++) {
			var trafo = new Transform();
			trafo.setPosition(new Vector3f(10*x,10*y,10*z));
            instances.add(trafo);
		}
	}
}

OpenGLContext.getInstance().execute(new java.lang.Runnable() {
	run: function() {
		for(var i = 0; i < count; i++) {
			world.getScene().getEntities().get(0).addInstance(instances.get(i));
		}
	}
});
