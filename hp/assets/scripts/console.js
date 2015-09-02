Vector3f = Java.type('org.lwjgl.util.vector.Vector3f');
Vector4f = Java.type('org.lwjgl.util.vector.Vector4f');
Quaternion = Java.type('org.lwjgl.util.vector.Quaternion');
AppContext = Java.type('engine.AppContext');

//print(world.getRenderer().getLightFactory().getDirectionalLight().getCamera().getPosition())

//(world.getScene().getDirectionalLight().setOrientation(new Quaternion(0, 0, 0, 1)))
print(AppContext.getInstance().getScene().getDirectionalLight().getOrientation())