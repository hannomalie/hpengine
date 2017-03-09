package de.hanno.hpengine.container;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.drawstrategy.extensions.DrawLinesExtension;
import de.hanno.hpengine.scene.AABB;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.util.stopwatch.StopWatch;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Octree implements LifeCycle, Serializable, EntitiesContainer {
	private static final long serialVersionUID = 1L;
	private static final ExecutorService executor = Executors.newFixedThreadPool(8);
	private static ExecutorService executorService = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors());
	private static Logger LOGGER = Logger.getLogger(Octree.class.getName());
	private static FloatBuffer matrix44Buffer = null;
	private static Matrix4f modelMatrix = new Matrix4f();
	public static final float DEFAULT_SIZE = 1000;
	public static final int DEFAULT_MAX_DEEPNESS = 6;
	public static boolean DRAW_LINES = false;
	static {
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
		matrix44Buffer.rewind();
		modelMatrix.setIdentity();
		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.rewind();
	}
	private Vector3f center = new Vector3f();
	private float size = DEFAULT_SIZE;
	public int maxDeepness = DEFAULT_MAX_DEEPNESS;

	private int currentDeepness = 0;

	public Node rootNode = new Node(this, center, size);

	private transient Map<Entity, Octree.Node> entityNodeMappings = new ConcurrentHashMap<>();

    public Octree() {
		this(new Vector3f());
	}

	public Octree(Vector3f center) {
		this(center, DEFAULT_SIZE);
	}
	public Octree(Vector3f center, float size) {
		this(center, size, DEFAULT_MAX_DEEPNESS);
	}
	public Octree(Vector3f center, int maxDeepness) { this(center, DEFAULT_SIZE, maxDeepness); }
	public Octree(Vector3f center, float size, int maxDeepness) {
		this.maxDeepness = maxDeepness;
		this.center = center;
		this.size = size;
	}

	@Override
    public void init() {
		LifeCycle.super.init();
		executorService = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors()/2);
		entityNodeMappings = new ConcurrentHashMap();
		this.rootNode = new Node(this, center, size);
		rootNode.span();
        Engine.getEventBus().register(this);
	}

	@Override
	public boolean isInitialized() {
		return true;
	}

	public List<Entity> getEntitiesForNode(Node node) { return Collections.unmodifiableList(entityNodeMappings.entrySet().stream().filter(pair -> pair.getValue().equals(node))
																					.map(pair -> pair.getKey()).collect(Collectors.toList()));}
	
	@Override
    public void insert(Entity entity) {

		if(entity.hasChildren()) {
			insert(entity.getChildren());
		}

	   Node insertedInto = rootNode.insert(entity);
	   if (insertedInto == null) {
		   entityNodeMappings.put(entity, rootNode);
	   } else {
		   entityNodeMappings.put(entity, insertedInto);
	   }
	   
	   rootNode.optimize();
//	   rootNode.optimizeThreaded();
	}

	public void insertWithoutOptimize(Entity entity) {

		if(entity.hasChildren()) {
			insert(entity.getChildren());
		}

	   Node insertedInto = rootNode.insert(entity);
	   if (insertedInto == null) {
		   entityNodeMappings.put(entity, rootNode);
	   } else {
		   entityNodeMappings.put(entity, insertedInto);
	   }
	}
	
	
	@Override
    public void insert(List<Entity> toDispatch){

        if(toDispatch == null) {
            return;
        }

		for (Entity Entity : toDispatch) {
			insertWithoutOptimize(Entity);
		}
		long start = System.currentTimeMillis();
	    rootNode.optimize();
		optimize();
//		rootNode.optimizeThreaded();
		long end = System.currentTimeMillis();
		LOGGER.info("Took " + (end - start) + " ms to optimize.");
	}

	@Override
    public List<Entity> getVisible(Camera camera) {
		StopWatch.getInstance().start("Octree get visible");
		List<Entity> result = new ArrayList<>();
//		result.addAll(getEntitiesForNode(rootNode));
//		rootNode.getVisible(de.hanno.hpengine.camera, result);
//		rootNode.getVisibleThreaded(de.hanno.hpengine.camera, result);

		result = getEntities().stream().filter(e -> e.isInFrustum((Camera) camera)).collect(Collectors.toList());
		StopWatch.getInstance().stopAndPrintMS();
		return new ArrayList<>(result);
	}
	
	
	
	public void optimize() {
		if(rootNode.hasChildren()) {
			for (Node node : rootNode.children) {
				node.optimize();
			}
		}
	}
	
	@Override
    public void drawDebug(Renderer renderer, Camera camera, Program program) {
//		program.setUniformAsMatrix4("modelMatrix", matrix44Buffer);
//		program.setUniform("materialDiffuseColor", new Vector3f(1,0,0));
//
//		// List of 24*3 = 72 floats per floatarray
//		List<float[]> arrays = rootNode.getPointsForLineDrawing(de.hanno.hpengine.camera);
//		float[] points = new float[arrays.size() * 72];
//		for (int i = 0; i < arrays.size(); i++) {
//			float[] array = arrays.get(i);
//			for (int z = 0; z < 72; z++) {
//				points[24*3*i + z] = array[z];
//			}
//
//		};
//		VertexBuffer buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3)).upload();
//		buffer.drawDebug();
//		buffer.delete();

        batchLines(rootNode);
        Renderer.getInstance().drawLines(program);
	}

	private void batchLines(Node node) {
        if(node.hasChildren()) {
            for(Node child : node.children) {
                batchLines(child);
            }
        } else if(node.hasEntities()){
            DrawLinesExtension.batchAABBLines(node.looseAabb.getMin(), node.looseAabb.getMax());
        }
    }

    /**
	 * children: index is clockwise 0-3 for top: left front, left back, right back, right front and 4-7 bottom: right back, right front, left front, left back 
	 * 
	 *
	 */
	public static class Node implements Serializable {
		private static final long serialVersionUID = 1L;

		Octree octree;
		Node parent;
		public Node[] children = new Node[8];
		Vector3f center;
		float size;
		AABB aabb;
		AABB looseAabb;
		private int deepness;
		private boolean hasChildren = false;

		public Node(Octree octree, Vector3f center, float size, int deepness) {
			this.octree = octree;
			this.center = center;
			this.size = size;
			this.deepness = deepness;
			if (octree.currentDeepness < deepness) {
				octree.currentDeepness = deepness;
			}
			this.aabb = new AABB(center, size);
			this.looseAabb = new AABB(center, 2*size);

//			LOGGER.de.hanno.hpengine.log(Level.INFO, "Created " + this.toString() + " with " + this.aabb.toString());
		}


		public void span() {
			if (deepness < octree.maxDeepness) {
				if(!hasChildren()) {
					for (int i = 0; i < 8; i++) {
						children[i] = new Node(this, i);
						children[i].span();
					}
					setHasChildren(true);
				}
			}
		}
		
		public boolean isLeaf() {
			return deepness == octree.maxDeepness;
		}
		
		public boolean isRoot() {
			return octree.rootNode == this;
		}
		
		public void getVisible(Camera camera, List<Entity> result) {
			if (isRoot() || isVisible(camera)) {
				if (hasChildren) {
					for(int i = 0; i < 8; i++) {
						children[i].getVisible(camera, result);
					}	
				}  else {
					result.addAll(octree.getEntitiesForNode(this));
				}
			}
		}
		

		private List<Entity> getVisibleThreaded(Camera camera, List<Entity> result) {

//			StopWatch.getInstance().start("Octree collects");
			ExecutorCompletionService ecs = new ExecutorCompletionService(executorService);
			List<Future<List<Entity>>> toGather = new ArrayList<Future<List<Entity>>>();
			
			if (isRoot()) {
				for(int i = 0; i < 8; i++) {
				      Callable<List<Entity>> worker = new CollectVisibleCallable(camera, children[i]);
				      Future<List<Entity>> submit = ecs.submit(worker);
				      toGather.add(submit);
				}	
			}
//			StopWatch.getInstance().stopAndPrintMS();

//			StopWatch.getInstance().start("Octree merge collected");
			for (Future<List<Entity>> future : toGather) {
			      try {
			        result.addAll(future.get());
			      } catch (InterruptedException e) {
			        e.printStackTrace();
			      } catch (ExecutionException e) {
			        e.printStackTrace();
			      }
			    }
			
//			collectExecutor.shutdown();
//			StopWatch.getInstance().stopAndPrintMS();
			return result;
		}

		public Collection<? extends Entity> getEntities() {
			return octree.getEntitiesForNode(this);
		}

		static class CollectVisibleCallable implements Callable<List<Entity>> {
			private Node node;
			private Camera camera;
			
			CollectVisibleCallable(Camera camera, Node node) {
			    this.node = node;
			    this.camera = camera;
			  }

			  @Override
			  public List<Entity> call() {
					List<Entity> result = new ArrayList<Entity>();
				  	node.getVisible(camera, result);
					return result;
			  }
		}
		
		public void getAllEntitiesInAndBelow(List<Entity> result) {

			if (hasChildren) {
				for(int i = 0; i < 8; i++) {
					children[i].getAllEntitiesInAndBelow(result);
				}
			}
			result.addAll(octree.getEntitiesForNode(this));
		}
		

		public boolean isVisible(Camera camera) {
			return looseAabb.isInFrustum(camera);
		}
		
		private List<Entity> getAllEntitiesInAndBelowThreaded() {

			ExecutorCompletionService ecs = new ExecutorCompletionService(executorService);
			List<Future<List<Entity>>> toGather = new ArrayList<Future<List<Entity>>>();
			List<Entity> result = new ArrayList<>();
			
			if (hasChildren) {
				for(int i = 0; i < 8; i++) {
				      Callable<List<Entity>> worker = new CollectEntitiesInAndBelowCallable(children[i]);
				      Future<List<Entity>> submit = ecs.submit(worker);
				      toGather.add(submit);
				}	
			}
			for (Future<List<Entity>> future : toGather) {
			      try {
			        result.addAll(future.get());
			      } catch (InterruptedException e) {
			        e.printStackTrace();
			      } catch (ExecutionException e) {
			        e.printStackTrace();
			      }
			    }
			
			result.addAll(octree.getEntitiesForNode(this));
			return result;
		}
		
		static class CollectEntitiesInAndBelowCallable implements Callable<List<Entity>> {
			private Node node;
			
			CollectEntitiesInAndBelowCallable(Node node) {
			    this.node = node;
			  }

			  @Override
			  public List<Entity> call() {
					List<Entity> result = new ArrayList<Entity>();
//					result.addAll(node.getAllEntitiesInAndBelow());
				  	node.getAllEntitiesInAndBelow(result);
					return result;
			  }
		}

		public List<float[]> getPointsForLineDrawing(Camera camera) {
			List<float[]> arrays = new ArrayList<float[]>();

			if (!isVisible(camera)) {return arrays;}
			List<Entity> temp = new ArrayList<>();
			getVisible(camera, temp);
			if (temp.isEmpty()) {return arrays;}
			
			arrays.add(getPoints());
			if (hasChildren()) {
				for (int i = 0; i < 8; i++) {
					arrays.addAll(children[i].getPointsForLineDrawing(camera));
				}
			}
			return arrays;
		}

		public List<float[]> getPointsForLineDrawing() {
			List<float[]> arrays = new ArrayList<float[]>();
				
			arrays.add(getPoints());
			if (hasChildren()) {
				for (int i = 0; i < 8; i++) {
					arrays.addAll(children[i].getPointsForLineDrawing());
				}
			}
			return arrays;
		}

		public Node(Octree octree, Vector3f center, float size) {
			this(octree, center, size, 0);
		}
		
		// index is clockwise 0-3 for top: left front, left back, right back, right front
		// and 4-7 bottom: right back, right front, left front, left back 
		private Node(Node parent, int index) {
			this(parent.octree, getCenterForNewChild(parent, index), parent.size/2, parent.deepness + 1);
		}
		
		// returns the node the entity was inserted into or null, if no insertion point was found
		public Node insert(Entity entity) {
//			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("Inserting %s ...", entity));

			Vector3f[] minMaxWorld = entity.getMinMaxWorld();
			
			if (isLeaf()) {
				if(contains(minMaxWorld)) {
					octree.entityNodeMappings.put(entity, this);
					return this;	
				} else {
					return null;
				}
			}
			
			if (hasChildren()) {
				for (int i = 0; i < 8; i++) {
					Node node = children[i];
					if (node.contains(minMaxWorld)) {
						if(node.contains(entity.getCenter())) {
							if(node.insert(entity) != null) {
								return node;
							}
						}
					}
				}
				
				// Wasn't able to add entity to children
				octree.entityNodeMappings.put(entity, this);
				return this;
				
			} else {
				octree.entityNodeMappings.put(entity, this);
				return this;
			}
		}
		
		private boolean contains(Vector3f position) {
			//return aabb.contains(new Vector4f(position.x, position.y, position.z, 1));
			return looseAabb.contains(new Vector4f(position.x, position.y, position.z, 1));
		}

		public void optimizeThreaded() {
			if (hasChildren()) {
				for (int i = 0; i < 8; i++) {
					Node node = children[i];
					
					Runnable worker = new OptimizeRunnable(node);
					executor.execute(worker);
				}
			    try {
					executor.shutdown();
					executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			    LOGGER.info("Finished all threads");
			}
		}
		public void optimize() {

			if (hasChildren()) {
				if(hasEntities() && !isRoot()) {
					List<Entity> collected = collectAllEntitiesFromChildren();
					for(Entity toInsert : collected) { octree.entityNodeMappings.put(toInsert, this); }
					setHasChildren(false);
				} else {
					for (int i = 0; i < 8; i++) {
						Node node = children[i];
						node.optimize();
					}
				}
			}
		}

		static class OptimizeRunnable implements Runnable {
			private Node node;
			
			OptimizeRunnable(Node node) {
			    this.node = node;
			  }

			  @Override
			  public void run() {
				  if (node.hasEntities() && node.hasChildren()) {
					node.addAll(node.collectAllEntitiesFromChildren());
					node.setHasChildren(false);
					LOGGER.info("Optimized...");
//					return;
				  }
				node.optimize();
			  }
		}

		private void addAll(List<Entity> entities) {
			for (Entity toAdd : entities) {
				octree.entityNodeMappings.put(toAdd, this);
			}
		}

		private List<Entity> collectAllEntitiesFromChildren() {
			
			List<Entity> result = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				Node node = children[i];

				if (!node.hasChildren()) {
					result.addAll(node.getEntities());
				} else {
					result.addAll(node.getEntities());
					result.addAll(node.collectAllEntitiesFromChildren());
				}
			}
			return result;
		}


		private boolean contains(Vector3f[] minMaxWorld) {
			Vector3f min = minMaxWorld[0];
			Vector3f max = minMaxWorld[1];
			
			if (looseAabb.contains(min) && looseAabb.contains(max)) {
//				LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) is in %s", min.x, min.y, min.z, aabb));
//				LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) is in %s", max.x, max.y, max.z, aabb));
				return true;
			}

//			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) not in %s", min.x, min.y, min.z, aabb));
//			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) not in %s", max.x, max.y, max.z, aabb));
			return false;
		}
		
		
		private static Vector3f getCenterForNewChild(Node parent, int index) {
			Vector3f newNodeCenter = new Vector3f(parent.center);
			float offset = parent.size/4;
			switch (index) {
			case 0:
				newNodeCenter.x -= offset;
				newNodeCenter.y += offset;
				newNodeCenter.z += offset;
				break;
			case 1:
				newNodeCenter.x -= offset;
				newNodeCenter.y += offset;
				newNodeCenter.z -= offset;
				break;
			case 2:
				newNodeCenter.x += offset;
				newNodeCenter.y += offset;
				newNodeCenter.z -= offset;
				break;
			case 3:
				newNodeCenter.x += offset;
				newNodeCenter.y += offset;
				newNodeCenter.z += offset;
				break;
			case 4:
				newNodeCenter.x += offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z -= offset;
				break;
			case 5:
				newNodeCenter.x += offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z += offset;
				break;
			case 6:
				newNodeCenter.x -= offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z += offset;
				break;
			case 7:
				newNodeCenter.x -= offset;
				newNodeCenter.y -= offset;
				newNodeCenter.z -= offset;
				break;

			default:
				break;
			}
			return newNodeCenter;
		}

		public boolean hasChildren() {
			return hasChildren;// && !hasEntities();
		}

		public void setHasChildren(boolean hasChildren) {
			this.hasChildren = hasChildren;
		}
		
		public boolean hasEntities() {
			return !getEntities().isEmpty();
		}
		
		public boolean hasEntitiesInChildNodes() {
			if (hasChildren) {
				for (int i = 0; i < children.length; i++) {
					if (children[i].hasEntitiesInChildNodes()) { return true; }
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return String.format("Node(%.2f) @ (%.2f, %.2f, %.2f)", size, center.x, center.y, center.z);
		}

		public Vector3f getCenter() {
			return new Vector3f(center);
		}

		public float getSize() {
			return size;
		}
		public int getMaxDeepness() {
			if (hasChildren()) {
				int childrenMaxDeepness = deepness;
				for (int i = 0; i < children.length; i++) {
					int d = children[i].getMaxDeepness();
					childrenMaxDeepness = d > childrenMaxDeepness? d : childrenMaxDeepness;
				}
				return childrenMaxDeepness;
			} else {
				return deepness;
			}
		}

		public void drawDebug(Renderer renderer, Program program) {
			VertexBuffer buffer = new VertexBuffer(getPoints(), EnumSet.of(DataChannels.POSITION3)).upload();
			buffer.drawDebug();
			if (hasChildren()) {
				for (int i = 0; i < 8; i++) {
					children[i].drawDebug(renderer, program);
				}
			}
		}

		private float[] getPoints() {
			return aabb.getPointsAsArray();
		}


		public int getDeepness() {
			return deepness;
		}
		
		private boolean checkValid() {
			boolean valid = false;
			if ((hasChildren() && getEntities().isEmpty()) || isRoot() || !hasChildren()) {
				valid = true;
			} else {
				valid = false;
			}
			return valid;
		}


		public boolean remove(Entity entity) {
			if (hasChildren) {
				for (int i = 0; i < children.length; i++) {
					if (children[i].getEntities().contains(entity)) {
						return children[i].remove(entity);
					}
				}
			}
			return octree.entityNodeMappings.remove(this, entity);
		}
	}

	@Override
    public List<Entity> getEntities() {
		return new CopyOnWriteArrayList<>(entityNodeMappings.keySet());
	}
	
	@Override
    public int getEntityCount() {
		return getEntities().size();
	}

	public int getCurrentDeepness() {
		return rootNode.getMaxDeepness();
	}

	@Override
    public boolean removeEntity(Entity entity) {
		entityNodeMappings.remove(entity);
		return rootNode.remove(entity);
	}

}
