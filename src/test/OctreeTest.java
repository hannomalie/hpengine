package test;

import static main.log.ConsoleLogger.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import junit.framework.Assert;
import main.Camera;
import main.DeferredRenderer;
import main.Entity;
import main.IEntity;
import main.Material;
import main.Renderer;
import main.Spotlight;
import main.octree.Box;
import main.octree.Octree;
import main.octree.Octree.Node;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.sun.javafx.geom.CubicApproximator;

public class OctreeTest {
	Renderer renderer;
	
	@Before
	public void init() {
		renderer = new DeferredRenderer(new Spotlight(true));
	}
	
	@Test
	public void generatingBox() {
		Box box = new Box(new Vector3f(), 10);
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
		Box box = new Box(new Vector3f(), 5);
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
		IEntity entity = new IEntity() {
			
			@Override
			public Vector3f getPosition() {
				return null;
			}
			@Override
			public String getName() {
				return null;
			}
			@Override
			public Material getMaterial() {
				return null;
			}
			@Override
			public void destroy() {
			}
			@Override
			public Vector4f[] getMinMaxWorld() {
				return new Vector4f[] {
						new Vector4f(-10, -10, -10, 0),
						new Vector4f(10, 10, 10, 0)};
			}
		};
		
		Octree octree = new Octree(new Vector3f(), 0);
		octree.insert(entity);
		Assert.assertFalse(octree.rootNode.hasChildren());
		Assert.assertEquals(octree.rootNode.getCenter(), new Vector3f());
		Assert.assertEquals(octree.rootNode.getSize(), Octree.defaultSize, 0.1f);
		Assert.assertEquals(0, octree.getCurrentDeepness());
		Assert.assertTrue(octree.rootNode.entities.contains(entity));
	}

	@Test
	public void quadNodeOctree() {
		IEntity entityBottomLeftBack = new IEntity() {
			
			@Override public Vector3f getPosition() { return null; }
			@Override public String getName() { return null; }
			@Override public Material getMaterial() { return null; }
			@Override public void destroy() { }
			@Override public Vector4f[] getMinMaxWorld() {
						return new Vector4f[] {
								new Vector4f(-5, -5, -5, 0),
								new Vector4f(-1, -1, -1, 0)};
			}

			@Override
			public boolean isInFrustum(Camera camera) {
				Vector4f[] minMaxWorld = getMinMaxWorld();
				Vector4f minWorld = minMaxWorld[0];
				Vector4f maxWorld = minMaxWorld[1];
				
				Vector3f centerWorld = new Vector3f();
				centerWorld.x = (maxWorld.x + minWorld.x)/2;
				centerWorld.y = (maxWorld.y + minWorld.y)/2;
				centerWorld.z = (maxWorld.z + minWorld.z)/2;
				
				Vector3f distVector = new Vector3f();
				Vector3f.sub(new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z),
								new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

				if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, distVector.length()/2)) {
					return true;
				}
				return false;
			}
		};
		IEntity entityTopRightFront = new IEntity() {
			
			@Override public Vector3f getPosition() { return null; }
			@Override public String getName() { return null; }
			@Override public Material getMaterial() { return null; }
			@Override public void destroy() { }
			@Override public Vector4f[] getMinMaxWorld() {
						return new Vector4f[] {
								new Vector4f(1, 1, 1, 0),
								new Vector4f(5, 5, 5, 0)};
			}

			@Override
			public boolean isInFrustum(Camera camera) {
				Vector4f[] minMaxWorld = getMinMaxWorld();
				Vector4f minWorld = minMaxWorld[0];
				Vector4f maxWorld = minMaxWorld[1];
				
				Vector3f centerWorld = new Vector3f();
				centerWorld.x = (maxWorld.x + minWorld.x)/2;
				centerWorld.y = (maxWorld.y + minWorld.y)/2;
				centerWorld.z = (maxWorld.z + minWorld.z)/2;
				
				Vector3f distVector = new Vector3f();
				Vector3f.sub(new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z),
								new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

				if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, distVector.length()/2)) {
					return true;
				}
				return false;
			}
		};
		
		Octree octree = new Octree(new Vector3f(), 10f, 1);
		octree.insert(entityBottomLeftBack);
		octree.insert(entityTopRightFront);
//		Assert.assertEquals(1, octree.getCurrentDeepness());
		
		Assert.assertEquals(5, octree.rootNode.children[0].getSize(), 0.1f);
		Assert.assertEquals(new Vector3f(-2.5f, 2.5f, 2.5f), octree.rootNode.children[0].getCenter());
		Assert.assertEquals(new Vector3f(2.5f, -2.5f, -2.5f), octree.rootNode.children[4].getCenter());
		
		Assert.assertTrue(octree.rootNode.children[7].entities.contains(entityBottomLeftBack));
		Assert.assertTrue(octree.rootNode.children[3].entities.contains(entityTopRightFront));
		
		// Octree culling
		Camera camera = new Camera(renderer); // cam is at 0 0 0 and looks into -z, up is y, right is x
		Assert.assertTrue(octree.rootNode.isVisible(camera));
		Assert.assertTrue(entityBottomLeftBack.isInFrustum(camera));
		Assert.assertFalse(entityTopRightFront.isInFrustum(camera));

		camera.move(new Vector3f(0, 0, -2));
		camera.update(1);
		Assert.assertTrue(octree.rootNode.isVisible(camera));
		Assert.assertTrue(octree.rootNode.children[1].isVisible(camera));
		Assert.assertTrue(octree.rootNode.children[2].isVisible(camera));
		Assert.assertFalse(octree.rootNode.children[0].isVisible(camera));
		Assert.assertFalse(octree.rootNode.children[3].isVisible(camera));
		
		List<IEntity> visibleEntities = octree.getVisible(camera);
		Assert.assertTrue(visibleEntities.contains(entityBottomLeftBack));
		Assert.assertFalse(visibleEntities.contains(entityTopRightFront));
	}
	
	@Test
//	@Ignore
	public void octreeInsertSpeedAndValidityTest() {
		getLogger().setLevel(Level.OFF);
		
		Octree octree = new Octree(new Vector3f(), 2000f, 4);
		Random random = new Random();
		final int entityCount = 10000;
		List<IEntity> toAdd = new ArrayList<>();
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
			
			IEntity entity = new IEntity() {
				
				@Override public Vector3f getPosition() { return null; }
				@Override public String getName() { return null; }
				@Override public Material getMaterial() { return null; }
				@Override public void destroy() { }
				@Override public Vector4f[] getMinMaxWorld() {
							return new Vector4f[] {
									new Vector4f(Math.min(rX1, rX2), Math.min(rY1, rY2), Math.min(rZ1, rZ2), 0),
									new Vector4f(Math.max(rX1, rX2), Math.max(rY1, rY2), Math.max(rZ1, rZ2), 0)};
				}
			};
//			octree.insert(entity);
			toAdd.add(entity);
		}

		long start = System.currentTimeMillis();
		octree.insert(toAdd);
		long end = System.currentTimeMillis();
		System.out.println("Took " + (end - start) + " ms to insert " + entityCount +  " entities.");
		System.out.println("Current deepness is " + octree.getCurrentDeepness());
		
		checkIfValid(octree.rootNode);
	}
	
	private void checkIfValid(Node node) {
		if (node.hasChildren()) {
			for (Node child : node.children) {
				checkIfValid(child);
			}
		}
		Assert.assertTrue(!node.hasChildren() ||
							(node.hasChildren() && node.entities.isEmpty()) ||
							node.getDeepness() == 0 );
	}
}
