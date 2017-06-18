package de.hanno.hpengine;

import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.container.Octree;
import de.hanno.hpengine.engine.container.Octree.Node;
import de.hanno.hpengine.engine.model.Entity;
import junit.framework.Assert;
import org.junit.Test;
import org.joml.Vector3f;
import org.joml.Vector4f;
import de.hanno.hpengine.engine.scene.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;

public class OctreeTest extends TestWithEngine {

    private static final Logger LOGGER = Logger.getLogger(OctreeTest.class.getName());

	@Test
	public void generatingBox() {
		AABB box = new AABB(new Vector3f(), 10);
		List<Vector3f> actual = box.getPoints();
		
		List<Vector3f> expected = new ArrayList<Vector3f>();
	    expected.add(new Vector3f(-5, -5, -5)); // bottom left back
		expected.add(new Vector3f(5, -5, -5)); 	// bottom right back
		expected.add(new Vector3f(5, -5, 5)); 	// bottom right front
		expected.add(new Vector3f(-5, -5, 5)); 	// bottom left front
		// bottom plane of the box
		expected.add(new Vector3f(5, 5, 5));	// top right front
		expected.add(new Vector3f(-5, 5, 5));	// top left front
		expected.add(new Vector3f(-5, 5, -5));	// top left back
		expected.add(new Vector3f(5, 5, -5));	// top left back
		
		Assert.assertTrue(expected.get(0).equals(actual.get(0)));
		Assert.assertTrue(expected.get(1).equals(actual.get(1)));
		Assert.assertTrue(expected.get(2).equals(actual.get(2)));
		Assert.assertTrue(expected.get(3).equals(actual.get(3)));
		Assert.assertTrue(expected.get(4).equals(actual.get(4)));
		Assert.assertTrue(expected.get(5).equals(actual.get(5)));
		Assert.assertTrue(expected.get(6).equals(actual.get(6)));
		Assert.assertTrue(expected.get(7).equals(actual.get(7)));
		
	}

	@Test
	public void boxContains() {
		AABB box = new AABB(new Vector3f(), 5);
		Vector4f pointInBox = new Vector4f(0,0,0,0);
		Vector4f pointInBox2 = new Vector4f(2.5f,0,0,0);
		Vector4f pointInBox3 = new Vector4f(2.5f,-2.5f,0f,0f);
		
		Vector4f pointOutOfBox = new Vector4f(10,0,0,0);
		Vector4f pointOutOfBox2 = new Vector4f(0,0,10,0);

		Assert.assertTrue(box.contains(pointInBox));
		Assert.assertTrue(box.contains(pointInBox2));
		Assert.assertTrue(box.contains(pointInBox3));
		Assert.assertFalse(box.contains(pointOutOfBox));
		Assert.assertFalse(box.contains(pointOutOfBox2));
	}
	
	@Test
	public void singleNodeOctree() {
		Entity entity = new Entity() {

			@Override public Vector3f getPosition() { return null; }
			@Override public String getName() { return "testName"; }
			@Override public void destroy() { }
			@Override
			public Vector3f[] getMinMaxWorld() {
				return new Vector3f[] {
						new Vector3f(-10, -10, -10),
						new Vector3f(10, 10, 10)};
			}
			@Override public boolean isSelected() { return false; }
			@Override public void setSelected(boolean selected) { }
			public Transform getTransform() {
				return null;
			}
		};
		
		Octree octree = new Octree(new Vector3f(), 0);
		octree.init();
		octree.insert(entity);
		Assert.assertFalse(octree.rootNode.hasChildren());
		Assert.assertEquals(octree.rootNode.getCenter(), new Vector3f());
		Assert.assertEquals(octree.rootNode.getSize(), Octree.DEFAULT_SIZE, 0.1f);
		Assert.assertEquals(0, octree.getCurrentDeepness());
		Assert.assertTrue(octree.rootNode.getEntities().contains(entity));
	}

	@Test
	public void quadNodeOctree() {
		Entity entityBottomLeftBack = new Entity() {
			
			@Override public Vector3f getPosition() { return new Vector3f(-3, -3, -3); }
			@Override public String getName() { return "entityBottomLeftBack"; }
			@Override public void destroy() { }
			@Override public Vector3f[] getMinMaxWorld() {
						return new Vector3f[] {
								new Vector3f(-5, -5, -5),
								new Vector3f(-1, -1, -1)};
			}

			@Override
			public boolean isInFrustum(Camera camera) {
				Vector3f[] minMaxWorld = getMinMaxWorld();
				Vector3f minWorld = minMaxWorld[0];
				Vector3f maxWorld = minMaxWorld[1];
				
				Vector3f centerWorld = new Vector3f();
				centerWorld.x = (maxWorld.x + minWorld.x)/2;
				centerWorld.y = (maxWorld.y + minWorld.y)/2;
				centerWorld.z = (maxWorld.z + minWorld.z)/2;
				
				Vector3f distVector = new Vector3f();
				new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z).sub(new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

				if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, distVector.length()/2)) {
					return true;
				}
				return false;
			}
			@Override public boolean isSelected() { return false; }
			@Override public void setSelected(boolean selected) { }
		};
		Entity entityTopRightFront = new Entity() {
			
			@Override public Vector3f getPosition() { return new Vector3f(3, 3, 3); }
			@Override public String getName() { return "entityTopRightFront"; }
			@Override public void destroy() { }
			@Override public Vector3f[] getMinMaxWorld() {
						return new Vector3f[] {
								new Vector3f(1, 1, 1),
								new Vector3f(5, 5, 5)};
			}

			@Override
			public boolean isInFrustum(Camera camera) {
				Vector3f[] minMaxWorld = getMinMaxWorld();
				Vector3f minWorld = minMaxWorld[0];
				Vector3f maxWorld = minMaxWorld[1];
				
				Vector3f centerWorld = new Vector3f();
				centerWorld.x = (maxWorld.x + minWorld.x)/2;
				centerWorld.y = (maxWorld.y + minWorld.y)/2;
				centerWorld.z = (maxWorld.z + minWorld.z)/2;
				
				Vector3f distVector = new Vector3f();
				new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z).sub(new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

				if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, distVector.length()/2)) {
					return true;
				}
				return false;
			}
			@Override public boolean isSelected() { return false; }
			@Override public void setSelected(boolean selected) { }
		};
		
		Octree octree = new Octree(new Vector3f(), 10f, 1);
		octree.init();
		octree.insert(entityBottomLeftBack);
		octree.insert(entityTopRightFront);
//		Assert.assertEquals(1, octree.getCurrentDeepness());
		
		Assert.assertEquals(5, octree.rootNode.children[0].getSize(), 0.1f);
		Assert.assertEquals(new Vector3f(-2.5f, 2.5f, 2.5f), octree.rootNode.children[0].getCenter());
		Assert.assertEquals(new Vector3f(2.5f, -2.5f, -2.5f), octree.rootNode.children[4].getCenter());
		
		Assert.assertTrue(octree.rootNode.children[7].getEntities().contains(entityBottomLeftBack));
		Assert.assertTrue(octree.rootNode.children[3].getEntities().contains(entityTopRightFront));
		
		// Octree culling
		Camera camera = new Camera();

		Assert.assertTrue(octree.rootNode.isVisible(camera));
		Assert.assertTrue(entityBottomLeftBack.isInFrustum((Camera) camera));
		Assert.assertFalse(entityTopRightFront.isInFrustum((Camera) camera));

		camera.translateLocal(new Vector3f(0, 0, -2));
		camera.update(1);
		Helpers.assertEpsilonEqual(new Vector3f(0,0,-2), camera.getPosition(), 0.001f);
		Helpers.assertEpsilonEqual(new Vector3f(0,0,-1), camera.getViewDirection(), 0.001f);
		camera.getFrustum().calculate(camera);
		Assert.assertTrue(octree.rootNode.isVisible(camera));
		Assert.assertTrue(octree.rootNode.children[0].isVisible(camera)); // left front
		Assert.assertFalse(octree.rootNode.children[1].isVisible(camera)); // left back
		Assert.assertFalse(octree.rootNode.children[2].isVisible(camera)); // right back
		Assert.assertTrue(octree.rootNode.children[3].isVisible(camera)); // right front
		
		List<Entity> visibleEntities = octree.getVisible(camera);
		Assert.assertTrue(visibleEntities.contains(entityBottomLeftBack));
		Assert.assertFalse(visibleEntities.contains(entityTopRightFront));
	}
	
	@Test
//	@Ignore
	public void octreeInsertSpeedAndValidityTest() {
		getLogger().setLevel(Level.OFF);
		
		Octree octree = new Octree(new Vector3f(), 2000f, 7);
		octree.init();
		Random random = new Random();
		final int entityCount = 10000;
		List<Entity> toAdd = new ArrayList<>();
		for (int i = 0; i < entityCount; i++) {

			final int rX1 = random.nextInt(2000) - 1000;
			final int rY1 = random.nextInt(2000) - 1000;
			final int rZ1 = random.nextInt(2000) - 1000;
			
			// I want entities with size of max 100 in each direction
			final int rAddX = random.nextInt(100) - 50;
			final int rAddY = random.nextInt(100) - 50;
			final int rAddZ = random.nextInt(100) - 50;
			
			final int rX2 = rX1 + rAddX;
			final int rY2 = rY1 + rAddY;
			final int rZ2 = rZ1 + rAddZ;
			
			final Vector3f[] minMax = new Vector3f[] {
					new Vector3f(Math.min(rX1, rX2), Math.min(rY1, rY2), Math.min(rZ1, rZ2)),
					new Vector3f(Math.max(rX1, rX2), Math.max(rY1, rY2), Math.max(rZ1, rZ2))};
			
			final Vector3f position = new Vector3f((minMax[0].x + minMax[1].x)/2,
											 (minMax[0].x + minMax[1].y)/2,
											 (minMax[0].x + minMax[1].z)/2);
			
			Entity entity = new Entity() {
				
				@Override public Vector3f getPosition() { return position; }
				@Override public String getName() { return null; }
				@Override public void destroy() { }
				@Override public Vector3f[] getMinMaxWorld() {
							return minMax;
				}
				@Override public boolean isSelected() { return false; }
				@Override public void setSelected(boolean selected) { }
			};
//			octree.insert(entity);
			toAdd.add(entity);
		}

		long start = System.currentTimeMillis();
		octree.insert(toAdd);
		long end = System.currentTimeMillis();
		LOGGER.info("Took " + (end - start) + " ms to insert " + entityCount +  " entities.");
		LOGGER.info("Current deepness is " + octree.getCurrentDeepness());
		
		checkIfValid(octree.rootNode);
	}
	
	private void checkIfValid(Node node) {
		if (node.hasChildren()) {
			for (Node child : node.children) {
				checkIfValid(child);
			}
		}
		Assert.assertTrue(!node.hasChildren() ||
							(node.hasChildren() && node.getEntities().isEmpty()) ||
							node.getDeepness() == 0 );
	}
}
