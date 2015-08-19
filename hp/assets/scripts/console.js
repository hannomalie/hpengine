Vector3f = Java.type('org.lwjgl.util.vector.Vector3f');
Vector4f = Java.type('org.lwjgl.util.vector.Vector4f');

//print(world.getRenderer().getLightFactory().getDirectionalLight().getCamera().getPosition())

world.getActiveCamera().move(new Vector3f(0,0,-5))