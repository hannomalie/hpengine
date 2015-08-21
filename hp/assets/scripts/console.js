Vector3f = Java.type('org.lwjgl.util.vector.Vector3f');
Vector4f = Java.type('org.lwjgl.util.vector.Vector4f');
Quaternion = Java.type('org.lwjgl.util.vector.Quaternion');

//print(world.getRenderer().getLightFactory().getDirectionalLight().getCamera().getPosition())

(world.getRenderer().getLightFactory().getDirectionalLight().setOrientation(new Quaternion(0.3341214 -0.7441488 0.6682428 0.3720744)))
print(world.getRenderer().getLightFactory().getDirectionalLight().getOrientation())