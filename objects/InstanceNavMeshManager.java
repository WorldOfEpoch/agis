package atavism.agis.objects;

import static org.recast4j.detour.DetourCommon.vCopy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import atavism.agis.plugins.AgisMobClient;
import atavism.agis.plugins.AgisMobPlugin;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.pathing.detour.DetourNumericReturn;
import atavism.server.pathing.recast.Helper;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

import org.recast4j.detour.*;
import org.recast4j.detour.NavMeshQuery.FRand;
import org.recast4j.detour.crowd.*;
import org.recast4j.detour.crowd.debug.CrowdAgentDebugInfo;
import org.recast4j.detour.extras.unity.astar.UnityAStarPathfindingImporter;
import org.recast4j.detour.io.MeshSetWriter;
import org.recast4j.dynamic.DynamicNavMesh;
import org.recast4j.dynamic.VoxelQuery;
import org.recast4j.dynamic.collider.*;
import org.recast4j.dynamic.io.VoxelFile;
import org.recast4j.dynamic.io.VoxelFileReader;
import java.nio.ByteOrder;

public class InstanceNavMeshManager implements Runnable {
	static final int MAX_VERTS_PER_POLY = 6;

	public InstanceNavMeshManager(String instanceName, OID instanceOid) {
		this.instanceOid = instanceOid;
		this.instanceName=instanceName;
		NavMeshLoadingManager nlm = new NavMeshLoadingManager(this);
		Engine.getExecutor().schedule(nlm, 100,  TimeUnit.MILLISECONDS);
	}

	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2 < 1 ? 1 : Runtime.getRuntime().availableProcessors() / 2, new DaemonThreadFactory());

    private static class DaemonThreadFactory implements ThreadFactory {
        private final AtomicLong count = new AtomicLong();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("DynamicNavMesh - " + count.getAndIncrement());
            return t;
        }
    }
	
	public void NavMeshLoad() {
		loadWorldNavMesh(instanceName);
	}
	
	public boolean loadWorldNavMesh(String name) {
		
		String navMeshAStarFilePath = "../navmesh/" + name + "/" + name + ".bytes";
		String navMeshAStarVoxelFilePath = "../navmesh/" + name + "/" + name + ".voxels";
		Log.error("loadWorldNavMesh Start for " + name + " ("+instanceOid+") navMesh:" + navMesh);
		UnityAStarPathfindingImporter importer = new UnityAStarPathfindingImporter();
		NavMesh[] meshes = null;

		try {
			File f = new File(navMeshAStarVoxelFilePath);
			if (f.exists()) {
				Log.error("loadWorldNavMesh: A* PathFinding Voxel file loading");
				InputStream is = new FileInputStream(f);
				Log.debug("loadWorldNavMesh: A* PathFinding Voxel 1");
				VoxelFileReader reader = new VoxelFileReader();
				Log.debug("loadWorldNavMesh: A* PathFinding Voxel 2");
				VoxelFile vf = reader.read(is);
				Log.debug("loadWorldNavMesh: A* PathFinding Voxel 3");
				dynamicNavMesh = new DynamicNavMesh(vf);
				//dynamicNavMesh.error
				Log.debug("loadWorldNavMesh: A* PathFinding Voxel 4");
				CompletableFuture<Boolean> future = dynamicNavMesh.build(executor);
				Log.debug("loadWorldNavMesh: A* PathFinding Voxel 5");
				future.get();
				Log.debug("loadWorldNavMesh: A* PathFinding Voxel 6");
				navMesh = dynamicNavMesh.navMesh();
				// navMeshQuery = new NavMeshQuery(mesh.navMesh());
				Log.debug("loadWorldNavMesh: A* PathFinding Voxel file loaded");
				if (AgisMobPlugin.DYNAMIC_NAVMESH_UPDATE_SAVE) {
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					writer.write(os, navMesh, ByteOrder.LITTLE_ENDIAN, true);
					try (OutputStream outputStream = new FileOutputStream("dynamicMesh-"+name+"-" + System.currentTimeMillis() + ".navmesh")) {
						os.writeTo(outputStream);
					}
				}
			}
		} catch (IOException | InterruptedException | ExecutionException e1) {
			Log.error("loadWorldNavMesh: Exception " + e1);
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Log.debug("loadWorldNavMesh |");
		if (navMesh == null) {
			try {
				File f = new File(navMeshAStarFilePath);
				if (f.exists()) {
					Log.error("loadWorldNavMesh: A* PathFinding Graph file loading");
					meshes = importer.load(new File(navMeshAStarFilePath)/* , MAX_VERTS_PER_POLY */);
				}
			} catch (Exception e) {
				e.printStackTrace();
				Log.exception("loadWorldNavMesh Graph ",e);
			}
		}
		if (meshes != null) {
			Log.error("loadWorldNavMesh: A* PathFinding file loaded");
			navMesh = meshes[0];
		} else {
		//	Log.error("Path XML file loaded");
		//	navMesh = loader.load2(navMesh);
		}
		if (Log.loggingTrace)
			Log.trace("loadWorldNavMesh: Start for " + name + " after load navMesh:" + navMesh);
		// if (navMeshLoaded) {
		if (navMesh != null) {
			if (Log.loggingTrace)
				Log.trace("loadWorldNavMesh: navMesh loaded for instance: " + name);
			if (Log.loggingTrace)
				Log.trace("loadWorldNavMesh: Crowd Start for " + name + " navMesh:" + navMesh);
			Log.debug("loadWorldNavMesh: Create NavMeshQuery from navMesh="+navMesh);
			if (Log.loggingTrace)
				Log.trace("Crowd Start for " + name);
			float[] cost= new float[] {1.0f,10.0f,1.0f,1.0f,2.0f,1.5f};
			filter = new DefaultQueryFilter(15,0,cost);
			CrowdConfig config = new CrowdConfig(0.5f);
			crowd = new Crowd(config, navMesh);
			if (Log.loggingTrace)
				Log.trace("loadWorldNavMesh: Crowd Start for " + name + " crowd:" + crowd);
			lastUpdate = System.currentTimeMillis();
			Engine.getExecutor().scheduleAtFixedRate(this, 500, 250, TimeUnit.MILLISECONDS);
			
			if(dynamicNavMesh!=null) {
				NavMeshDynamicObjectManager ndom = new NavMeshDynamicObjectManager(this);
				Engine.getExecutor().schedule(ndom, AgisMobPlugin.DYNAMIC_NAVMESH_UPDATE_INTERVAL,  TimeUnit.MILLISECONDS);
				
			}
			Log.error("loadWorldNavMesh End Loading for "+name);
			return true;
		} else {
			Log.error("loadWorldNavMesh: navMesh not loaded for instance: " + name);
		}
		Log.error("loadWorldNavMesh End Loading for "+name);
		// return navMeshLoaded;
		return false;
	}
	
	private class NavMeshLoadingManager implements Runnable {
	 	private InstanceNavMeshManager inm;
	 	public NavMeshLoadingManager(InstanceNavMeshManager inm) {
	 		this.inm =inm;
	 	}
        @Override
        public void run() {
        	Log.debug("NavMeshLoadingManager run ");
        	try {
				if(inm!=null) {
					inm.NavMeshLoad();
				}else {
					Log.debug("NavMeshLoadingManager run inm is null");
				}
			} catch (Exception e) {
				Log.exception("Loading ",e);
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	Log.debug("NavMeshLoadingManager run END");
        }
    }
	
	 private class NavMeshDynamicObjectManager implements Runnable {
		 	private InstanceNavMeshManager inm;
		 	public NavMeshDynamicObjectManager(InstanceNavMeshManager inm) {
		 		this.inm =inm;
		 	}
	        @Override
	        public void run() {
	        	Log.debug("NavMeshDynamicObjectManager run ");
	        	if(inm!=null) {
	        		inm.manageDynamicObjects();
	        	}else {
	        		Log.debug("NavMeshDynamicObjectManager run inm is null");
	        	}
	        }
	    }
	
	/**
	 * Adding to Queue 
	 * @param objects
	 */
	
	public void addDynamicObject(List<AtavismBuildingObject> objects) {
		Log.debug("addDynamicObject Start dynamicNavMesh="+dynamicNavMesh+" name="+instanceName);
		Log.debug("addDynamicObject queue size ="+dynamicObjectQueue.size());
		for(AtavismBuildingObject object : objects) {
			Log.debug("addDynamicObject "+object);
			dynamicObjectQueue.add(object);
		}
		Log.debug("addDynamicObject end queue size ="+dynamicObjectQueue.size());
	}
	
	
	/**
	 * 
	 * @param ids
	 */
	
	public void removeDynamicObject(List<Integer> ids) {
		Log.debug("removeDynamicObject Start dynamicNavMesh="+dynamicNavMesh+" name="+instanceName);
		Log.debug("removeDynamicObject queue size ="+dynamicObjectDelQueue.size());
		for(Integer id : ids) {
			Log.debug("removeDynamicObject "+id);
			dynamicObjectDelQueue.add(id);
		}
		Log.debug("removeDynamicObject End queue size ="+dynamicObjectDelQueue.size());
	}
	
	private final MeshSetWriter writer = new MeshSetWriter();
	
	/**
	 * 
	 */
	
	public void manageDynamicObjects() {
		Log.debug("manageDynamicObjects Start dynamicNavMesh="+dynamicNavMesh+" name="+instanceName);
		if (dynamicNavMesh != null) {
			try {
				int addedCollidersCount = 0;
				int deletedCollidersCount = 0;
				Log.debug("manageDynamicObjects dynamicObjectDelQueue size="+dynamicObjectDelQueue.size());
				
				while (dynamicObjectDelQueue.size() > 0) {
					Integer todell = dynamicObjectDelQueue.poll();
					AtavismBuildingObject abo = dynamicObjectSpawned.remove(todell);
					Log.debug("To delete "+todell+" "+abo);
					if (abo != null) {
						Log.debug("Colliders to remove "+abo.collidersId);
						for (Long id : abo.collidersId) {
							dynamicNavMesh.removeCollider(id);
							deletedCollidersCount++;
						}
					}
				}
				Log.debug("manageDynamicObjects dynamicObjectQueue size="+dynamicObjectQueue.size());
				while (dynamicObjectQueue.size() > 0) {
					List<Long> addColliders = new ArrayList<Long>();
					String a="Collider ";
					AtavismBuildingObject abo = dynamicObjectQueue.poll();
					if (abo != null) {
						if (dynamicObjectSpawned.containsKey(abo.id)) {
							AtavismBuildingObject abo2 = dynamicObjectSpawned.remove(abo.id);
							Log.debug("To delete " + abo2.id + " " + abo2);
							if (abo2 != null) {
								Log.debug("Colliders to remove " + abo2.collidersId);
								for (Long id : abo2.collidersId) {
									dynamicNavMesh.removeCollider(id);
									deletedCollidersCount++;
								}
							}
						}
						Log.debug("manageDynamicObjects abo " + abo);
						for (AtavismCollider collider : abo.colliders) {
							Log.debug("manageDynamicObjects collider " + collider);
							switch (collider.type) {
							case "Box":
								Log.debug("ADD Box Collider");
								AOVector pos = collider.position.rot(abo.getOrientation());
								float[] center = new float[] { pos.getX() + abo.getPosition().getX(), pos.getY() + abo.getPosition().getY(), pos.getZ() + abo.getPosition().getZ() };
								
								AOVector he0 =  collider.halfEdges.get(0).rot(abo.getOrientation());
								AOVector he1 =  collider.halfEdges.get(1).rot(abo.getOrientation());
								AOVector he2 =  collider.halfEdges.get(2).rot(abo.getOrientation());
								
								float[][] halfEdges = new float[][] { { he0.getX(), he0.getY(), he0.getZ() },
										{ he1.getX(), he1.getY(), he1.getZ() },
										{ he2.getX(), he2.getY(), he2.getZ() } };
								Log.debug("ADD Box Collider "+Arrays.toString(center)+" "+Arrays.deepToString(halfEdges));
								
								a += " box1 = new BoxCollider(new float[] { "+String.format ("%.2f",pos.getX() + abo.getPosition().getX())+"f,"+
								String.format ("%.2f",pos.getY() + abo.getPosition().getY())+"f,"+String.format ("%.2f",pos.getZ() + abo.getPosition().getZ())+"f },new float[][] {{"+
								String.format ("%.2f", he0.getX())+"f,"+String.format ("%.2f", he0.getY())+"f,"+String.format ("%.2f", he0.getZ())+"f },"+
							"{ "+String.format ("%.2f",he1.getX())+"f, "+String.format ("%.2f",he1.getY())+"f, "+String.format ("%.2f",he1.getZ())+"f },"+
							"{ "+String.format ("%.2f",he2.getX())+"f, "+String.format ("%.2f",he2.getY())+"f, "+String.format ("%.2f",he2.getZ())+"f } },0x1,0.1f);\n";
								a +="id = dynaMesh.addCollider(box1);\n" + "  colliders.put(id, box1);\n ";
								a +="colliderGizmos.put(id, ColliderGizmo.box(new float[] { "+String.format ("%.2f",pos.getX() + abo.getPosition().getX())+"f," + 
										String.format ("%.2f",pos.getY() + abo.getPosition().getY())+"f," +	String.format ("%.2f",pos.getZ() + abo.getPosition().getZ())+"f }, new float[][] {{" + 
										String.format ("%.2f", he0.getX())+"f,"+String.format ("%.2f", he0.getY())+"f,"+String.format ("%.2f", he0.getZ())+"f }," + 
										"{ "+String.format ("%.2f",he1.getX())+"f, "+String.format ("%.2f",he1.getY())+"f, "+String.format ("%.2f",he1.getZ())+"f }," + 
										"{ "+String.format ("%.2f",he2.getX())+"f, "+String.format ("%.2f",he2.getY())+"f, "+String.format ("%.2f",he2.getZ())+"f } }));\n\n";
								
								Collider box = new BoxCollider(center, halfEdges, 0x1, 0.1f);
								long boxColliderId = dynamicNavMesh.addCollider(box);
								addColliders.add(boxColliderId);
								break;
							case "Sphere":
								Log.debug("ADD Sphere Collider");
								AOVector posSphere = collider.position.rot(abo.getOrientation());
								float[] centerSphere = new float[] { posSphere.getX() + abo.getPosition().getX(), posSphere.getY() + abo.getPosition().getY(), posSphere.getZ() + abo.getPosition().getZ() };
								Collider sphere = new SphereCollider(centerSphere, collider.radius, 0x1, 0.1f);
								long sphereColliderId = dynamicNavMesh.addCollider(sphere);
								addColliders.add(sphereColliderId);
								break;
							case "Capsule":
								Log.debug("ADD Capsule Collider");
								AOVector che0 =  collider.halfEdges.get(0).rot(abo.getOrientation());
								AOVector che1 =  collider.halfEdges.get(1).rot(abo.getOrientation());
								float[] capsulePoint1 = new float[] { che0.getX() + abo.getPosition().getX(), che0.getY() + abo.getPosition().getY(), che0.getZ() + abo.getPosition().getZ() };
								float[] capsulePoint2 = new float[] { che1.getX() + abo.getPosition().getX(), che1.getY() + abo.getPosition().getY(), che1.getZ() + abo.getPosition().getZ() };
								Collider capsule = new CapsuleCollider(capsulePoint1, capsulePoint2, collider.radius, 0x1, 0.1f);
								long capsuleColliderId = dynamicNavMesh.addCollider(capsule);
								addColliders.add(capsuleColliderId);
								break;
							}
						}
						
					} else {
						Log.debug("manageDynamicObjects abo is null ");
						
					}
					//Log.error("id="+abo.getId()+" "+a);
					Log.debug("Colliders "+addColliders);
					if(addColliders.size()>0) {
						abo.collidersId.addAll(addColliders);
						dynamicObjectSpawned.put(abo.getId(), abo);
					}
					addedCollidersCount += addColliders.size();
				}
				Log.debug("manageDynamicObjects addedCollidersCount="+addedCollidersCount+" deletedCollidersCount="+deletedCollidersCount);
				if (addedCollidersCount > 0 || deletedCollidersCount > 0) {
					// update navmesh asynchronously
					Log.debug("manageDynamicObjects update");
					CompletableFuture<Boolean> future = dynamicNavMesh.update(executor);
					// wait for update to complete
					future.get();
					Log.debug("manageDynamicObjects mavmeshQuery");
					navMesh = dynamicNavMesh.navMesh();
					
					Log.debug("manageDynamicObjects write navmesh");
					
					if (AgisMobPlugin.DYNAMIC_NAVMESH_UPDATE_SAVE) {
						ByteArrayOutputStream os = new ByteArrayOutputStream();
						writer.write(os, navMesh, ByteOrder.LITTLE_ENDIAN, true);
						try (OutputStream outputStream = new FileOutputStream("dynamicMesh-" +instanceName+"-"+ System.currentTimeMillis() + ".navmesh")) {
							os.writeTo(outputStream);
						}
					}
					
				}
				
			     
			} catch (InterruptedException e) {
				Log.error("manageDynamicObjects InterruptedException" + e);
				e.printStackTrace();
			} catch (ExecutionException e) {
				Log.error("manageDynamicObjects ExecutionException" + e);
				e.printStackTrace();
			}
			/*
			 * catch (IOException e) { Log.error("addObject IOException"+e); }
			 */
			catch (Exception e) {
				Log.error("addObject Exception" + e);
				e.printStackTrace();
			} finally {
				Log.debug("manageDynamicObjects finally");
			}
		}
		Log.debug("manageDynamicObjects Schedule ");
		NavMeshDynamicObjectManager ndom = new NavMeshDynamicObjectManager(this);
		Engine.getExecutor().schedule(ndom, AgisMobPlugin.DYNAMIC_NAVMESH_UPDATE_INTERVAL,  TimeUnit.MILLISECONDS);
		Log.debug("manageDynamicObjects End ");
	}
	
	public void getDynamicObject(OID player) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "DNM_colliders");
		 props.put("clear", true);
		int total=0;
		int obj = 0;
		for(int id :dynamicObjectSpawned.keySet()) {
			AtavismBuildingObject abo = dynamicObjectSpawned.get(id);
			int col = 0;
			if(abo!=null) {
				
				for (AtavismCollider collider : abo.colliders) {
					if(Log.loggingDebug)Log.debug("manageDynamicObjects collider " + collider);
					switch (collider.type) {
					case "Box":
						Log.debug("ADD Box Collider");
						
						float[] center = new float[] { collider.position.getX() + abo.getPosition().getX(), collider.position.getY() + abo.getPosition().getY(), collider.position.getZ() + abo.getPosition().getZ() };
						float[][] halfEdges = new float[][] { { collider.halfEdges.get(0).getX(), collider.halfEdges.get(0).getY(), collider.halfEdges.get(0).getZ() },
								{ collider.halfEdges.get(1).getX(), collider.halfEdges.get(1).getY(), collider.halfEdges.get(1).getZ() },
								{ collider.halfEdges.get(2).getX(), collider.halfEdges.get(2).getY(), collider.halfEdges.get(2).getZ() } };
						if(Log.loggingDebug)Log.debug("ADD Box Collider "+Arrays.toString(center)+" "+Arrays.deepToString(halfEdges));
						props.put("t" + obj + "_" + col , "Box");
						props.put("p" + obj + "_" + col + "X", center[0]);
						props.put("p" + obj + "_" + col + "Y", center[1]);
						props.put("p" + obj + "_" + col + "Z", center[2]);

						props.put("p" + obj + "_" + col + "X0", halfEdges[0][0]);
						props.put("p" + obj + "_" + col + "Y0", halfEdges[0][1]);
						props.put("p" + obj + "_" + col + "Z0", halfEdges[0][2]);

						props.put("p" + obj + "_" + col + "X1", halfEdges[1][0]);
						props.put("p" + obj + "_" + col + "Y1", halfEdges[1][1]);
						props.put("p" + obj + "_" + col + "Z1", halfEdges[1][2]);

						props.put("p" + obj + "_" + col + "X2", halfEdges[2][0]);
						props.put("p" + obj + "_" + col + "Y2", halfEdges[2][1]);
						props.put("p" + obj + "_" + col + "Z2", halfEdges[2][2]);

						break;
					case "Sphere":
						Log.debug("ADD Sphere Collider");
						float[] centerSphere = new float[] { collider.position.getX() + abo.getPosition().getX(), collider.position.getY() + abo.getPosition().getY(), collider.position.getZ()  + abo.getPosition().getZ()};
						props.put("t" + obj + "_" + col , "Sphere");
						props.put("p" + obj + "_" + col + "X", centerSphere[0]);
						props.put("p" + obj + "_" + col + "Y", centerSphere[1]);
						props.put("p" + obj + "_" + col + "Z", centerSphere[2]);
						props.put("r" + obj + "_" + col , collider.radius);
						break;
					case "Capsule":
						Log.debug("ADD Capsule Collider");
						float[] capsulePoint1 = new float[] { collider.halfEdges.get(0).getX() + abo.getPosition().getX(), collider.halfEdges.get(0).getY() + abo.getPosition().getY(), collider.halfEdges.get(0).getZ() + abo.getPosition().getZ() };
						float[] capsulePoint2 = new float[] { collider.halfEdges.get(1).getX() + abo.getPosition().getX(), collider.halfEdges.get(1).getY() + abo.getPosition().getY(), collider.halfEdges.get(1).getZ() + abo.getPosition().getZ() };
						props.put("t" + obj + "_" + col , "Capsule");
						props.put("p" + obj + "_" + col + "X0", capsulePoint1[0]);
						props.put("p" + obj + "_" + col + "Y0", capsulePoint1[1]);
						props.put("p" + obj + "_" + col + "Z0", capsulePoint1[2]);
						props.put("p" + obj + "_" + col + "X1", capsulePoint2[0]);
						props.put("p" + obj + "_" + col + "Y1", capsulePoint2[1]);
						props.put("p" + obj + "_" + col + "Z1", capsulePoint2[2]);
						props.put("r" + obj + "_" + col , collider.radius);
						
						break;
					}
					col++;
					total++;
					if(total==30) {
						props.put("o" +obj + "num" , col );
						obj++;
						props.put("num" , obj );
						TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player, player, false, props);
						Engine.getAgent().sendBroadcast(msg);
						col=0;
						total=0;
						obj=0;
						 props = new HashMap<String, Serializable>();
						 props.put("ext_msg_subtype", "DNM_colliders");
						 props.put("clear", false);
					}
				}
				props.put("o" +obj + "num" , col );
				obj++;
				
				
			}
			props.put("num" , obj );
		}
		if(Log.loggingDebug)Log.debug("getDynamicObject props="+props);
		if(props.size()>1) {
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player, player, false, props);
		Engine.getAgent().sendBroadcast(msg);
		}
		
	}

	@Override
	public synchronized void run() {
		Log.debug("NAVMESH: running Update for instance: " + instanceOid);
		try {

			// First add all actors to add and remove all actors to remove
			Log.debug("NavMesh Run  "+instanceOid+" actorsToAdd "+actorsToAdd.size()+" actors "+actors.size()+" actorsToRemove "+actorsToRemove.size());
			for (DetourActor actor : actorsToAdd) {
				if (actor != null) {
					actors.add(actor);
					//Log.debug("DETOUR: added Actor: " + actor.getAgent().idx);
				}
			}
			actorsToAdd.clear();
			for (DetourActor actor : actorsToRemove) {
				crowd.removeAgent(actor.getAgent());
				actors.remove(actor);
			}
			actorsToRemove.clear();
			// Work out time since last update and then run the update
			float timeDif = (float) (System.currentTimeMillis() - lastUpdate) / 1000f;
			CrowdAgentDebugInfo info = new CrowdAgentDebugInfo();
			crowd.update(timeDif, info);
			lastUpdate = System.currentTimeMillis();
			// Log.debug("NAVMESH: running Update complete, moving onto Actors");
			for (DetourActor actor : actors) {
				if (actor == null || actor.getAgent() == null)
					continue;
				// Log.debug("NAVMESH: running update for actor: " + actor.getAgentId());

				float[] pos = actor.getAgent().npos;
				float[] vel = actor.getAgent().vel;
				if (Log.loggingDebug) Log.debug("NAVMESH: running updateDirLoc with vel: " + vel[0] + "," + vel[1] + "," + vel[2]+" pos: "+pos[0]+", "+pos[1]+", "+pos[2]);
				actor.updateDirLoc(new AOVector(vel[0], vel[1], vel[2]), new Point(pos[0], pos[1], pos[2]));
			}

		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			Log.error("ERROR: caught exception in InstanceMavMesh.Update: " + sw.toString());
		}
		Log.debug("NAVMESH: completed actor update");
	}

	public void addActor(OID actorOid, Point loc, DetourActor actor) {
		Log.debug("addactor "+actorOid+" loc "+loc +" instanceOid "+instanceOid);
		if (crowd == null) {
			Log.debug("DETOUR: no navMeshQuery so actor not added.");
			return;
		}
		if (Log.loggingDebug)Log.debug("addactor "+actorOid+" add to actorToAdd");
		actorsToAdd.add(actor);
		float[] pos = new float[3];
		pos[0] = loc.getX();
		pos[1] = loc.getY();
		pos[2] = loc.getZ();
		actor.addToNavMeshManager(this, crowd.addAgent(pos, actor.getParams()));
		actor.activate();
		Log.debug("addactor "+actorOid+" End");
		Log.debug("DETOUR: added Actor to addList: " + actorOid);
	}

	public void setActorTarget(OID actorOid, Point loc) {
		DetourActor actor = getDetourActorByOid(actorOid);
		if (actor != null) {
			float[] endPos = { loc.getX(), loc.getY(), loc.getZ() };
			float[] extents = { 4f, 4f, 4f };
			float[] nearestPt = new float[3];
            NavMeshQuery navMeshQuery = new NavMeshQuery(navMesh);
			long endRef = navMeshQuery.findNearestPoly(endPos, extents, filter/* , nearestPt */).result.getNearestRef();
			// .longValue;
			crowd.requestMoveTarget(actor.getAgent(), endRef, nearestPt);

		}
	}

	public void resetActorTarget(OID actorOid) {
		DetourActor actor = getDetourActorByOid(actorOid);
		if (actor != null) {
			crowd.resetMoveTarget(actor.getAgent());
		}
	}

	public void setActorSpeed(OID actorOid, float speed) {
		DetourActor actor = getDetourActorByOid(actorOid);
		if (actor != null) {
			actor.getAgent().params.maxSpeed = speed;
		}else {
			Log.error("setActorSpeed actor is null");
		}
	}

	public float getActorSpeed(OID actorOid) {
		DetourActor actor = getDetourActorByOid(actorOid);
		if (actor != null) {
			return actor.getAgent().params.maxSpeed;
		}else {
			Log.error("getActorSpeed actor is null for oid="+actorOid+" NaN");
		}

		return Float.NaN;
	}

	public void removeActor(OID actorOid) {
		Log.debug("DETOUR: remove actor: " + actorOid);
		DetourActor actor = getDetourActorByOid(actorOid);
		if (actor != null) {
			actor.deactivate();
			actorsToRemove.add(actor);
		}
	}

	public DetourActor getDetourActorByOid(OID actorOid) {
		if (Log.loggingDebug)	Log.debug("getDetourActorByOid actors "+actors.size());
		if (actorOid == null) {
			// Handle null actorOid case (optional, depending on your application logic)
			return null;
		}
		Iterator<DetourActor> iterator = actors.iterator();
		while (iterator.hasNext()) {
			DetourActor actor = iterator.next(); // Fetch the next element
			if (actor != null && actor.getOid().equals(actorOid)) {
				return actor;
			}
		}

		return null;
	}

	/*
	 * Path generation
	 */
	/*
	 * private boolean GetSteerTarget(NavMeshQuery navMeshQuery, float[] startPos,
	 * float[] endPos, float minTargetDistance, long[] path, int pathSize, ref
	 * float[] steerPos, ref short steerPosFlag, ref long steerPosRef) { float[]
	 * outPoints = null; int outPointsCount = 0; return GetSteerTarget(navMeshQuery,
	 * startPos, endPos, minTargetDistance, path, pathSize, ref steerPos, ref
	 * steerPosFlag, ref steerPosRef, ref outPoints, ref outPointsCount); }
	 */
	public void ShowPolyToPlayer() {
		if (Log.loggingTrace)
			Log.trace("ShowPolyToPlayer: ------------------------------------------------------------------------------");
		LinkedList<OID> players1 = AgisMobClient.GetPlayersOnline();
		Map<String, Serializable> props1 = new HashMap<String, Serializable>();
		int pos1 = 0;
		float[] teststartPos = new float[3];
		int maxTile = navMesh.getMaxTiles();
		for (int i = 0; i < maxTile; i++) {
			MeshTile mt = navMesh.getTile(i);
			for (Poly p : mt.data.polys) {
				if (pos1 > 4000)
					break;
				vCopy(teststartPos, mt.data.verts, p.verts[0] * 3);
				props1.put("path_" + pos1 + "X", teststartPos[0]);
				props1.put("path_" + pos1 + "Y", teststartPos[1]);
				props1.put("path_" + pos1 + "Z", teststartPos[2]);
				pos1++;
				vCopy(teststartPos, mt.data.verts, p.verts[1] * 3);
				props1.put("path_" + pos1 + "X", teststartPos[0]);
				props1.put("path_" + pos1 + "Y", teststartPos[1]);
				props1.put("path_" + pos1 + "Z", teststartPos[2]);
				pos1++;
				vCopy(teststartPos, mt.data.verts, p.verts[2] * 3);
				props1.put("path_" + pos1 + "X", teststartPos[0]);
				props1.put("path_" + pos1 + "Y", teststartPos[1]);
				props1.put("path_" + pos1 + "Z", teststartPos[2]);
				pos1++;
			}

		}
		if (Log.loggingTrace)
			Log.trace("ShowPolyToPlayer: pos1:" + pos1);
		/*
		 * for (Long ll : rh.path) { Tupple2<MeshTile, Poly> mp =
		 * navMesh.getTileAndPolyByRef(ll); //mp.second.vertes //
		 * Log.error("GeneratePath verts:"+Arrays.toString(mp.second.verts)+" reff:"+ll)
		 * ; vCopy(teststartPos, mp.first.data.verts, mp.second.verts[0] * 3);
		 * props1.put("path_" + pos1 + "X", teststartPos[0]); props1.put("path_" + pos1
		 * + "Y", teststartPos[1]); props1.put("path_" + pos1 + "Z", teststartPos[2]);
		 * pos1++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * vCopy(teststartPos, mp.first.data.verts, mp.second.verts[1] * 3);
		 * props1.put("path_" + pos1 + "X", teststartPos[0]); props1.put("path_" + pos1
		 * + "Y", teststartPos[1]); props1.put("path_" + pos1 + "Z", teststartPos[2]);
		 * pos1++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * vCopy(teststartPos, mp.first.data.verts, mp.second.verts[2] * 3);
		 * props1.put("path_" + pos1 + "X", teststartPos[0]); props1.put("path_" + pos1
		 * + "Y", teststartPos[1]); props1.put("path_" + pos1 + "Z",
		 * teststartPos[2]);pos1++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll); }
		 */
		float[] startPos = new float[] { -1000.02557f, 55.117336f, 1000.38442f };
		float[] endPos = new float[] { -1000.14f, 56.52f, 1000.32f };
		props1.put("ext_msg_subtype", "NavPoints4");
		/*
		 * props1.put("current_X", startPos[0]); props1.put("current_Y", startPos[1]);
		 * props1.put("current_Z", startPos[2]); props1.put("destination_X", endPos[0]);
		 * props1.put("destination_Y", endPos[1]); props1.put("destination_Z",
		 * endPos[2]);
		 */
		props1.put("numPoints", pos1);
		for (OID player : players1) {
			if (Log.loggingTrace)
				Log.trace("ShowPolyToPlayer: Sending Path to " + player);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player, player, false, props1);
			Engine.getAgent().sendBroadcast(msg);// 000000000001d989 121225
		}

		if (Log.loggingTrace)
			Log.trace("ShowPolyToPlayer: ------------------------------------------------------------------------------");

	}


	public OID debugPlayer;
	public void setDebugPlayer(OID ply){
		if(Log.loggingDebug)Log.debug("Set Debug Player "+ply);
		debugPlayer = ply;
	}
	boolean checkVisibility(Point startPosition, Point endPosition) {
		long start = System.nanoTime();
		if (Log.loggingDebug)
			Log.debug("Insctance NavMesh: checkVisibility start ");
		float[] startPos = new float[] { startPosition.getX(), startPosition.getY(), startPosition.getZ() };
		float[] endPos = new float[] { endPosition.getX(), endPosition.getY(), endPosition.getZ() };
		
		/*if(dynamicNavMesh!=null) {
			
			 VoxelQuery vquery = dynamicNavMesh.voxelQuery();
		       // float[] start = { 7.4f, 0.5f, -64.8f };
		       // float[] end = { 31.2f, 0.5f, -75.3f };
		        Optional<Float> hit = vquery.raycast(startPos, endPos);
		        hit.
		        if(hit!=null) {
		        	hit.isEmpty();
		        }else {
		        	
		        }
		       // assertThat(hit).isEmpty();
		        
		        
		}else */
		{
			float[] extents = new float[] { 1f, 3f, 1f };
		
		if (Log.loggingDebug)
			Log.debug("Insctance NavMesh: checkVisibility startPos:" + Arrays.toString(startPos) + " endPos:" + Arrays.toString(endPos) + " extents:" + Arrays.toString(extents));
        NavMeshQuery navMeshQuery = new NavMeshQuery(navMesh);
		Result<FindNearestPolyResult> fnprStart = navMeshQuery.findNearestPoly(startPos, extents, filter);
		float[] nearestPoint = null;
		Long startRef = fnprStart.result.getNearestRef();
		nearestPoint = fnprStart.result.getNearestPos();
		if (Log.loggingDebug)
			Log.debug("checkVisibility: startRef:" + startRef + " Start nearestPoint:" + Arrays.toString(nearestPoint));
		if (startRef == 0) {
			return true;
		}
		Result<FindNearestPolyResult> fnprEnd = navMeshQuery.findNearestPoly(endPos, extents, filter);
		Long endRef = fnprEnd.result.getNearestRef();
		nearestPoint = fnprEnd.result.getNearestPos();
		if (Log.loggingDebug)
			Log.debug("checkVisibility: endRef:" + endRef + " End nearestPoint:" + Arrays.toString(nearestPoint));
		if (endRef == 0) {
			return true;
		}
		if (startRef.equals(endRef)) {
			if (Log.loggingTrace)
				Log.debug("checkVisibility: Start end End Ponts on same Poly return true runTime ns: " + ((System.nanoTime() - start)));
			return true;
		}

		Result<RaycastHit> rh = navMeshQuery.raycast(startRef, startPos, endPos, filter, 1, 0);
		if (Log.loggingDebug)
			Log.debug("checkVisibility: raycast hitEdgeIndex:" + rh.result.hitEdgeIndex + " pathCost:" + rh.result.pathCost + " path:" + rh.result.path + " hitNormal:" + Arrays.toString(rh.result.hitNormal) + " " + rh.result.t);
		if (rh.result.path.size() > 0)
			if (rh.result.path.get(rh.result.path.size() - 1).equals(endRef)) {
				if (Log.loggingDebug)
					Log.debug("checkVisibility: Start end End In line Raycast return true runTime ns: " + ((System.nanoTime() - start)));
				return true;
			}
		}
		if (Log.loggingDebug)
			Log.debug("checkVisibility: Start end End not In line Raycast return false runTime ns: " + ((System.nanoTime() - start)));
		return false;
	}

	public Optional<Float> checkVisibilityNew(Point startPosition, Point endPosition) {
		if (Log.loggingDebug)
			Log.debug("Instance NavMesh: checkVisibilityNew start ");
		float[] startPos = new float[] { startPosition.getX(), startPosition.getY()+1f, startPosition.getZ() };
		float[] endPos = new float[] { endPosition.getX(), endPosition.getY()+1f, endPosition.getZ() };
		if (dynamicNavMesh != null) {
			if(Log.loggingDebug)Log.debug("Instance NavMesh: checkVisibilityNew startPos="+Arrays.toString(startPos)+" endPos="+Arrays.toString(endPos)+" "+instanceName+" "+instanceOid);
			VoxelQuery query = dynamicNavMesh.voxelQuery();
			Optional<Float> hit = query.raycast(startPos, endPos);
			if(Log.loggingDebug)Log.debug("Instance NavMesh: checkVisibilityNew hit "+hit);
			if(debugPlayer!=null){
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "DNM_visibility");
				props.put("sPx",startPos[0]);
				props.put("sPy",startPos[1]);
				props.put("sPz",startPos[2]);
				props.put("ePx",endPos[0]);
				props.put("ePy",endPos[1]);
				props.put("ePz",endPos[2]);
				props.put("hit",hit.isPresent());
				if(hit.isPresent()) {
					float[] raycastHitPos = hit.map(t -> new float[]{startPos[0] + t * (endPos[0] - startPos[0]), startPos[1] + t * (endPos[1] - startPos[1]),	startPos[2] + t * (endPos[2] - startPos[2])}).orElse(endPos);
					props.put("hitX", raycastHitPos[0]);
					props.put("hitY", raycastHitPos[1]);
					props.put("hitZ", raycastHitPos[2]);
				}
				if(Log.loggingDebug)Log.debug("checkVisibilityNew: props:"+props);
				TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, debugPlayer, debugPlayer, false, props);
				Engine.getAgent().sendBroadcast(msg);
			}
			return hit;
		}else {
			Log.debug("Instance NavMesh: checkVisibility not found dynamicNavMesh");
		}
		
		return Optional.empty();
	}
	
	public Point findNearestPoint(Point point) {
		if (Log.loggingDebug)Log.debug("findNearestPoint: point "+point);
		float[] _point = new float[] { point.getX(), point.getY(), point.getZ() };
		float[] extents = new float[] { 1f, 3f, 1f };
        NavMeshQuery navMeshQuery = new NavMeshQuery(navMesh);
		if (Log.loggingDebug)Log.debug("findNearestPoint: _point="+_point+" extents="+extents+" navMeshQuery "+navMeshQuery);
		Result<FindNearestPolyResult> fnprStart = navMeshQuery.findNearestPoly(_point, extents, filter);
		if (Log.loggingDebug)Log.debug("findNearestPoint: fnprStart="+fnprStart);
		Long startRef = fnprStart.result.getNearestRef();
		float[]	nearestPoint = fnprStart.result.getNearestPos();
		if (Log.loggingDebug)Log.debug("findNearestPoint: startRef="+startRef+" nearestPoint="+nearestPoint);
		//if (Log.loggingTrace)startRef
			if (Log.loggingDebug)Log.debug("GeneratePathDragonsan: startRef:" + startRef + " nearestPoint:" + Arrays.toString(nearestPoint));
	    if(startRef==0) {
	    	return null;
	    }
	    Point p = new Point(nearestPoint[0],nearestPoint[1],nearestPoint[2]);
	    if (Log.loggingDebug)Log.debug("findNearestPoint: end point "+p);
	    return p;
	}
	
	
	public Point findRandomPointAroundCircle(Point centerPos, float maxRadius) {
		float[] _centerPos = new float[] { centerPos.getX(), centerPos.getY(), centerPos.getZ() };
		float[] extents = new float[] { 1f, 3f, 1f };
		float[] nearestPoint = null;
        NavMeshQuery navMeshQuery = new NavMeshQuery(navMesh);
		Result<FindNearestPolyResult> fnprStart = navMeshQuery.findNearestPoly(_centerPos, extents, filter);
		Long startRef = fnprStart.result.getNearestRef();
		nearestPoint = fnprStart.result.getNearestPos();
		FRand f = new FRand();
		//Log.error("findRandomPointAroundCircle"+);
		Result<FindRandomPointResult> point = navMeshQuery.findRandomPointWithinCircle(startRef, nearestPoint, maxRadius, filter, f);
		Point p = new Point(point.result.getRandomPt()[0],point.result.getRandomPt()[1],point.result.getRandomPt()[2]);
		return p;
		
	}
	
	public ArrayList<AOVector> GeneratePathDragonsan(Point startPosition, Point endPosition) {
		float[] startPos = new float[] { startPosition.getX(), startPosition.getY(), startPosition.getZ() };
		float[] endPos = new float[] { endPosition.getX(), endPosition.getY(), endPosition.getZ() };
		ArrayList<AOVector> pathPoints = new ArrayList<AOVector>();
		//if (Log.loggingTrace)
			Log.debug("GeneratePathDragonsan: ---------------------------------------------------------------------------");
		//if (Log.loggingTrace)
			Log.debug("GeneratePathDragonsan: startPos:" + Arrays.toString(startPos) + " endPos:" + Arrays.toString(endPos));
		float[] extents = new float[] { 1f, 2f, 1f };
		float[] nearestPoint = null;
		int MaxPolys = 256;
		// Status status = _navMeshQuery.FindNearestPoly(startPos, extents, filter, ref startRef, ref nearestPt);
        NavMeshQuery navMeshQuery = new NavMeshQuery(navMesh);
		Result<FindNearestPolyResult> fnprStart = navMeshQuery.findNearestPoly(startPos, extents, filter);
		Long startRef = fnprStart.result.getNearestRef();
		nearestPoint = fnprStart.result.getNearestPos();
		//if (Log.loggingTrace)
			Log.debug("GeneratePathDragonsan: startRef:" + startRef + " nearestPoint:" + Arrays.toString(nearestPoint));
		Result<FindNearestPolyResult> fnprEnd = navMeshQuery.findNearestPoly(endPos, extents, filter);
		Long endRef = fnprEnd.result.getNearestRef();
		nearestPoint = fnprEnd.result.getNearestPos();
		if(endPosition.equals(startPosition)){
			AOVector endPoint = new AOVector(endPosition);
			AOVector startPoint = new AOVector(startPosition);
			pathPoints.add(startPoint);
			pathPoints.add(endPoint);
			//if (Log.loggingTrace)
			Log.debug("GeneratePathDragonsan: Start end End Points on same Poly return path");
			return pathPoints;
		}
		//if (Log.loggingTrace)
			Log.debug("GeneratePathDragonsan: endRef:" + endRef + " nearestPoint:" + Arrays.toString(nearestPoint));

		if(startRef == 0 && endRef != 0){
			AOVector endPoint = new AOVector(endPosition);
			AOVector startPoint = new AOVector(startPosition);
			pathPoints.add(startPoint);
			pathPoints.add(endPoint);
			//if (Log.loggingTrace)
			Log.debug("GeneratePathDragonsan: Start point is out the navmesh  return path");
			return pathPoints;
		}

		if (startRef==0 || endRef==0) {
			Log.warn("GeneratePathDragonsan startPosition="+startPosition+" endPosition="+endPosition+" not found nearestPoints for start or end "+startRef+" "+endRef);
			return null;
		}
		if (startRef.equals(endRef)) {
			AOVector endPoint = new AOVector(endPosition);
			AOVector startPoint = new AOVector(startPosition);
			pathPoints.add(startPoint);
			pathPoints.add(endPoint);
			//if (Log.loggingTrace)
				Log.debug("GeneratePathDragonsan: Start end End Points on same Poly return path");
			return pathPoints;
		}

		Result<RaycastHit> rh = navMeshQuery.raycast(startRef, startPos, endPos, filter, /* options */1, /* prevRef */0);
	 if (Log.loggingDebug)
			Log.debug(
					"GeneratePathDragonsan: raycast hitEdgeIndex:" + rh.result.hitEdgeIndex + " pathCost:" + rh.result.pathCost + " path:" + rh.result.path + " hitNormal:" + Arrays.toString(rh.result.hitNormal));
		if (rh.result.path.size() > 0)
			if (rh.result.path.get(rh.result.path.size() - 1).equals(endRef)) {
				AOVector endPoint = new AOVector(endPosition);
				AOVector startPoint = new AOVector(startPosition);
				pathPoints.add(startPoint);
				pathPoints.add(endPoint);
					Log.debug("GeneratePathDragonsan: Start end End In line Raycast return path");
				return pathPoints;
			}

	
		Result<List<Long>> findPathResult = navMeshQuery.findPath(startRef, endRef, startPos, endPos, filter/* , path, 256 */);
		List<Long> refPolys = findPathResult.result;
		
		if (Log.loggingDebug)
			Log.debug("GeneratePathDragonsan: findPath refPolys:" + refPolys + " "+findPathResult.succeeded());

		boolean refStatus = findPathResult.succeeded();
		pathPoints.add(new AOVector(startPosition));

//      Code for debugging
//		LinkedList<OID> players1 = AgisMobClient.GetPlayersOnline();
//		Map<String, Serializable> props1 = new HashMap<String, Serializable>();
//		int pos1 = 0;
//		int pos2 = 0;
//
//		float[] teststartPos = new float[3];
//
//		for (Long ll : refPolys) {
//			Result<Tupple2<MeshTile, Poly>> mp = navMesh.getTileAndPolyByRef(ll);
//			Poly p = mp.result.second;
//			MeshTile t = mp.result.first;
//
//			// Log.error("GeneratePath verts:"+Arrays.toString(mp.second.verts)+" reff:"+ll);
//			vCopy(teststartPos, t.data.verts, p.verts[0] * 3);
//			AOVector point1 = new AOVector(teststartPos[0], teststartPos[1], teststartPos[2]);
//
//			props1.put("path_" + pos1 + "X", teststartPos[0]);
//			props1.put("path_" + pos1 + "Y", teststartPos[1]);
//			props1.put("path_" + pos1 + "Z", teststartPos[2]);
//			pos1++;
//			// Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
//			vCopy(teststartPos, t.data.verts, p.verts[1] * 3);
//			AOVector point2 = new AOVector(teststartPos[0], teststartPos[1], teststartPos[2]);
//			props1.put("path_" + pos1 + "X", teststartPos[0]);
//			props1.put("path_" + pos1 + "Y", teststartPos[1]);
//			props1.put("path_" + pos1 + "Z", teststartPos[2]);
//			pos1++;
//			// Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
//			vCopy(teststartPos, t.data.verts, p.verts[2] * 3);
//			AOVector point3 = new AOVector(teststartPos[0], teststartPos[1], teststartPos[2]);
//			props1.put("path_" + pos1 + "X", teststartPos[0]);
//			props1.put("path_" + pos1 + "Y", teststartPos[1]);
//			props1.put("path_" + pos1 + "Z", teststartPos[2]);
//			pos1++;
//			// Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
//			AOVector point4 = point1.add(point2).add(point3).multiply(1f / 3f);
//			props1.put("path1_" + pos2 + "X", point4.getX());
//			props1.put("path1_" + pos2 + "Y", point4.getY());
//			props1.put("path1_" + pos2 + "Z", point4.getZ());
//			pos2++;
//
//			if (!ll.equals(startRef) && !ll.equals(endRef))
//				pathPoints.add(point4);
//			if (ll.equals(endRef))
//				pathPoints.add(new AOVector(endPosition));
//
//		} // for
//
//		props1.put("ext_msg_subtype", "NavPoints5");
//		props1.put("current_X", startPos[0]);
//		props1.put("current_Y", startPos[1]);
//		props1.put("current_Z", startPos[2]);
//		props1.put("destination_X", endPos[0]);
//		props1.put("destination_Y", endPos[1]);
//		props1.put("destination_Z", endPos[2]);
//		props1.put("numPoints", pos1);
//		props1.put("numPoints1", pos2);
//		if (Log.loggingTrace)
//			Log.trace("GeneratePathDragonsan: Sending Path  props " + props1 + " " + props1.size());
//		//Send to Client vertexs of path
//		for (OID player : players1) {
//			if (Log.loggingTrace)
//				Log.trace("GeneratePathDragonsan: Sending Path to " + player + " " + props1 + " " + props1.size());
//			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player, player, false, props1);
//			Engine.getAgent().sendBroadcast(msg);
//		}

		boolean funnel = true;

		// Recast
		if (!funnel)
			if (pathPoints.size() > 2) {
			//	if (Log.loggingTrace)
					Log.debug("GeneratePathDragonsan: Raycast for Points Calculation");

				ArrayList<AOVector> pathRayPoints = new ArrayList<AOVector>();
				pathRayPoints.add(pathPoints.get(0));
				float[] rayStartPoint = new float[] { pathPoints.get(0).getX(), pathPoints.get(0).getY(), pathPoints.get(0).getZ() };
				Long rayStartPointRef = startRef;
				float[] rayPrevPoint = new float[] { pathPoints.get(0).getX(), pathPoints.get(0).getY(), pathPoints.get(0).getZ() };
				Long rayPrevPointRef = startRef;
				for (int i = 1; i < pathPoints.size(); i++) {
					float[] pathPos = new float[] { pathPoints.get(i).getX(), pathPoints.get(i).getY(), pathPoints.get(i).getZ() };
					Result<FindNearestPolyResult> ResultFindPolyRef = navMeshQuery.findNearestPoly(pathPos, extents, filter);
					Long polyRef = ResultFindPolyRef.result.getNearestRef();
					if (Log.loggingTrace)
						Log.trace("GeneratePathDragonsan: Raycast " + i + " rayStartPointRef:" + rayStartPointRef + " rayStartPoint:" + Arrays.toString(rayStartPoint) + " polyPont:"
								+ Arrays.toString(pathPos) + " polyRef:" + polyRef);
					Result<RaycastHit> rch = navMeshQuery.raycast(rayStartPointRef, rayStartPoint, pathPos, filter, 1, 0);
					if (Log.loggingTrace)
						Log.trace("GeneratePathDragonsan: Raycast " + i + " hitEdgeIndex:" + rch.result.hitEdgeIndex + " pathCost:" + rch.result.pathCost + " path:" + rch.result.path + " hitNormal:"
								+ Arrays.toString(rch.result.hitNormal));
					if (!rch.result.path.get(rch.result.path.size() - 1).equals(polyRef)) {
						if (Log.loggingTrace)
							Log.trace("GeneratePathDragonsan: Raycast " + i + " no inline add prev point to end point list and set prev as start point");
						pathRayPoints.add(new AOVector(rayPrevPoint[0], rayPrevPoint[1], rayPrevPoint[2]));
						System.arraycopy(rayPrevPoint, 0, rayStartPoint, 0, 3);
						rayStartPointRef = rayPrevPointRef;
					} else {
						if (Log.loggingTrace)
							Log.trace("GeneratePathDragonsan: Raycast " + i + " inline Set Prev point ");
						System.arraycopy(pathPos, 0, rayPrevPoint, 0, 3);
						rayPrevPointRef = polyRef;
					}

				}
				pathRayPoints.add(pathPoints.get(pathPoints.size() - 1));
				//if (Log.loggingTrace)
					Log.debug("GeneratePathDragonsan: Raycast return path "+pathRayPoints);
				return pathRayPoints;
			}

		// funnel
		if (funnel) {
			//if (Log.loggingTrace)
				Log.debug("GeneratePathDragonsan: Funnel Start Calculation ");
			ArrayList<AOVector> pathFunnelPoints = new ArrayList<AOVector>();

			/*
			
			Long funnelStartRef = refPolys.get(0);
			float[] funnelStartPoint = new float[3];
			float[] funnelVertexPos = new float[3];
			System.arraycopy(startPos, 0, funnelStartPoint, 0, 3);

			for (int i = 1; i < refPolys.size(); i++) {
				Tupple2<MeshTile, Poly> pathPoly = navMesh.getTileAndPolyByRef(refPolys.get(i));

				vCopy(funnelVertexPos, pathPoly.first.data.verts, pathPoly.second.verts[0] * 3);
				// Vertex 0 of poly
				RaycastHit rch0 = navMeshQuery.raycast(funnelStartRef, funnelStartPoint, funnelVertexPos, filter, 1, 0);
				Log.error("GeneratePathDragonsan: Funnel " + i + " raycast0 funnelVertexPos:" + Arrays.toString(funnelVertexPos) + " hitEdgeIndex:" + rch0.hitEdgeIndex + " pathCost:" + rch0.pathCost + " path:"
						+ rch0.path + " hitNormal:" + Arrays.toString(rch0.hitNormal));

				vCopy(funnelVertexPos, pathPoly.first.data.verts, pathPoly.second.verts[1] * 3);// Vertex 0 of poly
				RaycastHit rch1 = navMeshQuery.raycast(funnelStartRef, funnelStartPoint, funnelVertexPos, filter, 1, 0);
				Log.error("GeneratePathDragonsan: Funnel " + i + " raycast1 funnelVertexPos:" + Arrays.toString(funnelVertexPos) + " hitEdgeIndex:" + rch1.hitEdgeIndex + " pathCost:" + rch1.pathCost + " path:"
						+ rch1.path + " hitNormal:" + Arrays.toString(rch1.hitNormal));

				vCopy(funnelVertexPos, pathPoly.first.data.verts, pathPoly.second.verts[2] * 3);// Vertex 0 of poly
				RaycastHit rch2 = navMeshQuery.raycast(funnelStartRef, funnelStartPoint, funnelVertexPos, filter, 1, 0);
				Log.error("GeneratePathDragonsan: Funnel " + i + " raycast2 funnelVertexPos:" + Arrays.toString(funnelVertexPos) + " hitEdgeIndex:" + rch2.hitEdgeIndex + " pathCost:" + rch2.pathCost + " path:"
						+ rch2.path + " hitNormal:" + Arrays.toString(rch2.hitNormal));

			}
			 */
			if (!refPolys.isEmpty()) {
				// In case of partial path, make sure the end point is clamped to the last
				// polygon.
				float[] epos = new float[] { endPos[0], endPos[1], endPos[2] };
				if (refPolys.get(refPolys.size() - 1) != endRef) {
					Result<ClosestPointOnPolyResult> result = navMeshQuery.closestPointOnPoly(refPolys.get(refPolys.size() - 1), endPos);
					if (result.succeeded()) {
						epos = result.result.getClosest();
					}
				}
				Log.debug("GeneratePathDragonsan: Funnel startPos="+startPos+" endPos="+endPos+" epos="+epos);
				Result<List<StraightPathItem>> spi = navMeshQuery.findStraightPath(startPos, epos, refPolys, 2048, 0);
				int ii = 0;
				for (StraightPathItem sp : spi.result) {
					pathFunnelPoints.add(new AOVector(sp.getPos()[0], sp.getPos()[1], sp.getPos()[2]));

				}
			}
		//	if (Log.loggingTrace)
				Log.debug("GeneratePathDragonsan: Funnel return " + pathFunnelPoints);
			return pathFunnelPoints;
			
		}

		if (Log.loggingTrace)
			Log.trace("GeneratePathDragonsan: refStatus:" + refStatus + " refPolys:" + refPolys + " refPolys.size:" + refPolys.size());
		List<Long> smoothPolys = new ArrayList<Long>(refPolys);
		// System.arraycopy(refPolys, 0, smoothPolys, 0, refPolys.size());
		int smoothPolyCount = refPolys.size();
		float[] iterPos = new float[3], targetPos = new float[3];
		boolean posOverPoly = false;
		Result<ClosestPointOnPolyResult> closestStart = navMeshQuery.closestPointOnPoly(startRef, startPos);
		posOverPoly = closestStart.result.isPosOverPoly();
		iterPos = closestStart.result.getClosest();
		if (Log.loggingTrace)
			Log.trace("GeneratePathDragonsan: closestPointOnPoly iterPos:" + Arrays.toString(iterPos) + " posOverPoly:" + posOverPoly);
		Result<ClosestPointOnPolyResult> closest = navMeshQuery.closestPointOnPoly(smoothPolys.get(smoothPolyCount - 1), endPos);
		targetPos = closest.result.getClosest();
		posOverPoly = closest.result.isPosOverPoly();
		if (Log.loggingTrace)
			Log.trace("GeneratePathDragonsan: closestPointOnPoly targetPos:" + Arrays.toString(targetPos) + " posOverPoly:" + posOverPoly);

		float StepSize = 0.5f;
		float Slop = 0.01f;
		float[] SmoothPath = new float[/* 2048 */512 * 3];

		int SmoothPathNum = 0;
		System.arraycopy(iterPos, 0, SmoothPath, SmoothPathNum * 3, 3);
		SmoothPathNum++;

		while (smoothPolyCount > 0 && SmoothPathNum < /* 2048 */512) {

			// Find location to steer towards.
			float[] steerPos = new float[3];
			short steerPosFlag = 0;
			long steerPosRef = 0;
			DetourNumericReturn returnSteerTarget = GetSteerTarget(navMeshQuery, iterPos, targetPos, Slop, smoothPolys,	smoothPolyCount);
			if (!returnSteerTarget.boolValue)
				break;
			steerPos = returnSteerTarget.floatArrayValue;

			steerPosFlag = (short) returnSteerTarget.intValue;
			steerPosRef = returnSteerTarget.longValue;
			if (Log.loggingTrace)
				Log.trace("GeneratePathDragonsan: steerPos:" + Arrays.toString(steerPos) + " steerPosFlag:" + steerPosFlag + " steerPosRef:" + steerPosRef);
			boolean endOfPath = (steerPosFlag & StraightPathEnd) != 0;
			boolean offMeshConnection = (steerPosFlag & StraightPathOffMeshConnection) != 0;

			// Find movement delta.
			float[] delta = Helper.VSub(steerPos[0], steerPos[1], steerPos[2], iterPos[0], iterPos[1], iterPos[2]);
			float len = (float) Math.sqrt(Helper.VDot(delta, delta));

			// If the steer target is end of path or off-mesh link, do not move past the
			// location.
			if ((endOfPath || offMeshConnection) && len < StepSize) {
				len = 1;
			} else {
				len = StepSize / len;
			}
			float[] moveTarget = new float[3];
			Helper.VMad(moveTarget, iterPos, delta, len);

			// Move
			float[] result = new float[3];
			List<Long> visited;// new long[16];
			int nVisited = 0;
			Result<MoveAlongSurfaceResult> masr = navMeshQuery.moveAlongSurface(smoothPolys.get(0), iterPos, moveTarget, filter );
			result = masr.result.getResultPos();
			visited = masr.result.getVisited();
			nVisited = masr.result.getVisited().size();
			if (Log.loggingTrace)
				Log.trace("GeneratePathDragonsan: moveAlongSurface result:" + Arrays.toString(result) + " nVisited:" + nVisited + " visited:" + visited);
			float[] teststartPos1 = new float[3];
			float[] teststartPos2 = new float[3];
			float[] teststartPos3 = new float[3];

			for (Long ll : visited) {
				Result<Tupple2<MeshTile, Poly>> mp = navMesh.getTileAndPolyByRef(ll);
				vCopy(teststartPos1, mp.result.first.data.verts, mp.result.second.verts[0] * 3);
				vCopy(teststartPos2, mp.result.first.data.verts, mp.result.second.verts[1] * 3);
				vCopy(teststartPos3, mp.result.first.data.verts, mp.result.second.verts[2] * 3);
				// Log.error("GeneratePathDragonsan: result1:"+Arrays.toString(teststartPos1)+"
				// result2:"+Arrays.toString(teststartPos2)+"
				// result3:"+Arrays.toString(teststartPos3) );
			}
			smoothPolyCount = FixupCorridor(smoothPolys, smoothPolyCount, MaxPolys, visited, nVisited);
			smoothPolyCount = FixupShortcuts(smoothPolys, smoothPolyCount, navMeshQuery);

			// float h = 0;
			//result[1] = 
			Result<Float> rf =	navMeshQuery.getPolyHeight(smoothPolys.get(0), result/* , ref h */);
			if(rf.succeeded()) {
				result[1] = rf.result;
			}
			System.arraycopy(result, 0, iterPos, 0, 3);

			// Handle end of path and off-mesh links when close enough.
			if (endOfPath && InRange(iterPos, steerPos, Slop, 1.0f)) {
				if (Log.loggingTrace)
					Log.trace("endOfPath && InRange(iterPos, steerPos, Slop, 1.0f " + endOfPath + " " + InRange(iterPos, steerPos, Slop, 1.0f));
				// Reached end of path.
				System.arraycopy(targetPos, 0, iterPos, 0, 3);
				if (SmoothPathNum < /* 2048 */512) {
					System.arraycopy(iterPos, 0, SmoothPath, SmoothPathNum * 3, 3);
					SmoothPathNum++;
				}
				break;
			} else if (offMeshConnection && InRange(iterPos, steerPos, Slop, 1.0f)) {
				if (Log.loggingTrace)
					Log.trace("else if (offMeshConnection && InRange(iterPos, steerPos, Slop, 1.0f " + offMeshConnection + " " + InRange(iterPos, steerPos, Slop, 1.0f));
				// Reached off-mesh connection.
				float[] startPosOffMesh = new float[3], endPosOffMesh = new float[3];

				// Advance the path up to and over the off-mesh connection.
				long prevRef = 0, polyRef = smoothPolys.get(0);
				int npos = 0;
				while (npos < smoothPolyCount && polyRef != steerPosRef) {
					prevRef = polyRef;
					polyRef = smoothPolys.get(npos);
					npos++;
				}
				for (int i = npos; i < smoothPolyCount; i++) {
					smoothPolys.set(i - npos, smoothPolys.get(i));
				}
				smoothPolyCount -= npos;

				// Handle the connection.
				// Status status =
				Result<Tupple2<float[], float[]>> meshConnectionPolyEndPoints = navMeshQuery.getAttachedNavMesh().getOffMeshConnectionPolyEndPoints(prevRef,
						polyRef/* , ref startPosOffMesh, ref endPosOffMesh */);
				startPosOffMesh = meshConnectionPolyEndPoints.result.first;
				endPosOffMesh = meshConnectionPolyEndPoints.result.second;
				if (meshConnectionPolyEndPoints != null/* (status & Status.SUCCSESS) != 0 */) {
					if (SmoothPathNum < MaxSmooth) {
						System.arraycopy(startPosOffMesh, 0, SmoothPath, SmoothPathNum * 3, 3);
						SmoothPathNum++;
						// Hack to make the dotted path not visible during off-mesh connection.
						if ((SmoothPathNum & 1) == 1) {
							System.arraycopy(startPosOffMesh, 0, SmoothPath, SmoothPathNum * 3, 3);
							SmoothPathNum++;
						}
					}
					// Move position at the other side of the off-mesh link.
					System.arraycopy(endPosOffMesh, 0, iterPos, 0, 3);
					// float eh = 0.0f;
					//iterPos[1] = 
					Result<Float> ft = navMeshQuery.getPolyHeight(smoothPolys.get(0), iterPos/* , ref eh */);
					if(rf.succeeded()) {
						iterPos[1] = rf.result;
					}
					// iterPos[1] = eh;
				}
			}

			// Store results.
			if (SmoothPathNum < /* 2048 */512) {
				System.arraycopy(iterPos, 0, SmoothPath, SmoothPathNum * 3, 3);
				SmoothPathNum++;
			}
		}

		// ArrayList<AOVector> pathPoints = new ArrayList<AOVector>();
		if (SmoothPathNum > 2) {
			// Remove any points that don't provide a new direction
			int pointsToRemoveCount = 0;
			List<Float> points = new ArrayList<Float>();// = null;//new List<float>();
			AOVector lastDirection = new AOVector(0, 0, 0);
			AOVector prev = new AOVector(SmoothPath[0], SmoothPath[1], SmoothPath[2]);
			for (int i = 1; i < SmoothPathNum; i++) {
				AOVector currentPoint = new AOVector(SmoothPath[i * 3 + 0], SmoothPath[i * 3 + 1] + 0.1f, SmoothPath[i * 3 + 2]);
				AOVector currentDirection = AOVector.sub(currentPoint, prev).normalize();
				float dif = AOVector.sub(currentDirection, lastDirection).length();
				// Debug.Log("CurrentDirection: " + currentDirection + " and lastDirection:" +
				// lastDirection + " and dif: " + dif);
				if (Math.abs(dif) < 0.01f) {
					pointsToRemoveCount++;
				} else {
					points.add(SmoothPath[i * 3 - 3]);
					points.add(SmoothPath[i * 3 - 2]);
					points.add(SmoothPath[i * 3 - 1]);
				}
				prev = currentPoint;
				lastDirection = currentDirection;
			}
			points.add(SmoothPath[SmoothPathNum * 3 - 3]);
			points.add(SmoothPath[SmoothPathNum * 3 - 2]);
			points.add(SmoothPath[SmoothPathNum * 3 - 1]);
			if (Log.loggingTrace)
				Log.trace("GeneratePathDragonsan: Last point: " + SmoothPath[SmoothPathNum * 3 - 3] + "," + SmoothPath[SmoothPathNum * 3 - 2] + "," + SmoothPath[SmoothPathNum * 3 - 1]);
			SmoothPathNum -= pointsToRemoveCount;
			for (int i = 1; i < SmoothPathNum; i++) {
				SmoothPath[i * 3 + 0] = points.get(i * 3 + 0);
				SmoothPath[i * 3 + 1] = points.get(i * 3 + 1);
				SmoothPath[i * 3 + 2] = points.get(i * 3 + 2);
				pathPoints.add(new AOVector(SmoothPath[i * 3 + 0], SmoothPath[i * 3 + 1], SmoothPath[i * 3 + 2]));

			}

		}
		// Write path to log
		String pathPointsList = "PATH: ";
		for (AOVector point : pathPoints) {
			pathPointsList += point.toString() + "; ";
		}
		if (Log.loggingTrace)
			Log.trace("GeneratePathDragonsan: " + pathPointsList + " count:" + pathPoints.size());
		LinkedList<OID> players = AgisMobClient.GetPlayersOnline();
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		int pos = 0;
		for (AOVector point : pathPoints) {
			props.put("path_" + pos + "X", point.getX());
			props.put("path_" + pos + "Y", point.getY());
			props.put("path_" + pos + "Z", point.getZ());
			pos++;
		}
		// props.put("numPoints", pos);
		props.put("ext_msg_subtype", "NavPoints3");
		props.put("current_X", startPos[0]);
		props.put("current_Y", startPos[1]);
		props.put("current_Z", startPos[2]);
		props.put("destination_X", endPos[0]);
		props.put("destination_Y", endPos[1]);
		props.put("destination_Z", endPos[2]);
		props.put("numPoints", pos);
		for (OID player : players) {
			if (Log.loggingTrace)
				Log.trace("Sending Path to " + player);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player, player, false, props);
			Engine.getAgent().sendBroadcast(msg);// 000000000001d989 121225
		}

		if (Log.loggingTrace)
			Log.trace("GeneratePathDragonsan: --------------------------------------------------------------------------------------------");
		// ArrayList<AOVector> pathPoints = new ArrayList<AOVector>();
		return pathPoints;
	}

	public ArrayList<AOVector> GeneratePath(Point startPosition, Point endPosition) {
		int maxPolys = 256;
		float[] startPos = new float[] { startPosition.getX(), startPosition.getY(), startPosition.getZ() };
		float[] endPos = new float[] { endPosition.getX(), endPosition.getY(), endPosition.getZ() };
		float[] extents = new float[] { 2f, 4f, 2f };
		float[] nearestPoint = null;
		if (Log.loggingTrace)
			Log.trace("GeneratePath startPos:" + Arrays.toString(startPos) + " endPos:" + Arrays.toString(endPos) + " " + filter);
		if (Log.loggingTrace)
			System.out.println("GeneratePath startPos:" + Arrays.toString(startPos) + " endPos:" + Arrays.toString(endPos) + " " + filter);
		// DetourNumericReturn numericReturn = navMeshQuery.findNearestPoly(startPos,
		// extents, filter, nearestPoint);
        NavMeshQuery navMeshQuery = new NavMeshQuery(navMesh);
		Result<FindNearestPolyResult> numericReturn = navMeshQuery.findNearestPoly(startPos, extents, filter/* , nearestPoint */);
		long startRef = numericReturn.result.getNearestRef();
		nearestPoint = numericReturn.result.getNearestPos();
		if (Log.loggingTrace)
			Log.trace("GeneratePath startRef:" + startRef + " nearestPoint:" + Arrays.toString(nearestPoint));
		if (Log.loggingTrace)
			System.out.println("GeneratePath startRef:" + startRef + " nearestPoint:" + Arrays.toString(nearestPoint));
		// long startRef = numericReturn.longValue;
		// Log.debug("PATH: Found start position status: " + numericReturn.status + ",
		// Ref " + startRef + ", pos " + startPos[0] + "," + startPos[1] + "," +
		// startPos[2]);
		// numericReturn = navMeshQuery.findNearestPoly(endPos, extents, filter,
		// nearestPoint);
		numericReturn = navMeshQuery.findNearestPoly(endPos, extents, filter/* , nearestPoint */);
		// long endRef = numericReturn.longValue;
		long endRef = numericReturn.result.getNearestRef();
		nearestPoint = numericReturn.result.getNearestPos();
		if (Log.loggingTrace)
			Log.trace("GeneratePath endRef:" + endRef + " nearestPoint:" + Arrays.toString(nearestPoint));
		if (Log.loggingTrace)
			System.out.println("GeneratePath endRef:" + endRef + " nearestPoint:" + Arrays.toString(nearestPoint));
		// Log.debug("PATH: Found end position status: " + numericReturn.status + ", Ref
		// " + endRef + ", pos " + endPos[0] + "," + endPos[1] + "," + endPos[2]);
		// DetourStatusReturn statusReturn = navMeshQuery.findPath(startRef, endRef,
		// startPos, endPos, filter/*, path, 256*/);
		Result<List<Long>> statusReturn = navMeshQuery.findPath(startRef, endRef, startPos, endPos, filter/* , path, 256 */);
		// path = Long statusReturn.getRefs().to;
		if (Log.loggingTrace)
			Log.trace("GeneratePath FindPathResult:" + statusReturn.result + " size:" + statusReturn.result.size() + " status:" + statusReturn.succeeded());
		long[] path = new long[statusReturn.result.size()];

		int ii = 0;
		for (Long ll : statusReturn.result)
			path[ii++] = ll;
		List<Long> path2 = statusReturn.result;
		// int polyCount = statusReturn.intValue;
		int polyCount = statusReturn.result.size();
		if (polyCount < 1) {
			return null;
		}

		// zb start
		/*
		 * LinkedList<OID> players = AgisMobClient.GetPlayersOnline(); Map<String,
		 * Serializable> props = new HashMap<String, Serializable>(); int pos =0;
		 * float[] teststartPos = new float[3]; for (Long ll : statusReturn.getRefs()) {
		 * Tupple2<MeshTile, Poly> mp = navMesh.getTileAndPolyByRef(ll);
		 * //mp.second.vertes //
		 * Log.error("GeneratePath verts:"+Arrays.toString(mp.second.verts)+" reff:"+ll)
		 * ; vCopy(teststartPos, mp.first.data.verts, mp.second.verts[0] * 3);
		 * props.put("path_" + pos + "X", teststartPos[0]); props.put("path_" + pos +
		 * "Y", teststartPos[1]); props.put("path_" + pos + "Z", teststartPos[2]);
		 * pos++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * vCopy(teststartPos, mp.first.data.verts, mp.second.verts[1] * 3);
		 * props.put("path_" + pos + "X", teststartPos[0]); props.put("path_" + pos +
		 * "Y", teststartPos[1]); props.put("path_" + pos + "Z", teststartPos[2]);
		 * pos++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * vCopy(teststartPos, mp.first.data.verts, mp.second.verts[2] * 3);
		 * props.put("path_" + pos + "X", teststartPos[0]); props.put("path_" + pos +
		 * "Y", teststartPos[1]); props.put("path_" + pos + "Z", teststartPos[2]);pos++;
		 * //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * // vCopy(teststartPos, mp.first.data.verts, mp.second.verts[3] * 3); //
		 * props.put("path_" + pos + "X", teststartPos[0]); // props.put("path_" + pos +
		 * "Y", teststartPos[1]); // props.put("path_" + pos + "Z",
		 * teststartPos[2]);pos++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * // vCopy(teststartPos, mp.first.data.verts, mp.second.verts[4] * 3); //
		 * props.put("path_" + pos + "X", teststartPos[0]); // props.put("path_" + pos +
		 * "Y", teststartPos[1]); // props.put("path_" + pos + "Z",
		 * teststartPos[2]);pos++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * // vCopy(teststartPos, mp.first.data.verts, mp.second.verts[5] * 3); //
		 * props.put("path_" + pos + "X", teststartPos[0]); // props.put("path_" + pos +
		 * "Y", teststartPos[1]); // props.put("path_" + pos + "Z",
		 * teststartPos[2]);pos++; //
		 * Log.error("GeneratePath verts:"+Arrays.toString(teststartPos)+" reff:"+ll);
		 * 
		 * // for (int lli : mp.second.verts) { // // }
		 * 
		 * } //MoveAlongSurfaceResult masr = navMeshQuery.moveAlongSurface(path[0],
		 * startPos, endPos, filter); //
		 * Log.error("GeneratePath point:"+Arrays.toString(masr.getResultPos())+" "+masr
		 * .getVisited()); props.put("ext_msg_subtype", "NavPoints2");
		 * props.put("current_X", startPos[0]); props.put("current_Y", startPos[1]);
		 * props.put("current_Z", startPos[2]); props.put("destination_X", endPos[0]);
		 * props.put("destination_Y", endPos[1]); props.put("destination_Z", endPos[2]);
		 * props.put("numPoints", pos); for (OID player : players) {
		 * Log.error("Sending Path to "+player); TargetedExtensionMessage msg = new
		 * TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, player,
		 * player, false, props); Engine.getAgent().sendBroadcast(msg);//
		 * 000000000001d989 121225 }
		 */

		// zb end
		long[] smoothPolys = new long[maxPolys];
		System.arraycopy(path, 0, smoothPolys, 0, polyCount);
		int smoothPolyCount = polyCount;
		float[] smoothPath = new float[2048 * 3];

		float[] iterPos = new float[3], targetPos = new float[3];
		Result<ClosestPointOnPolyResult> cpopr = navMeshQuery.closestPointOnPoly(startRef, startPos/* , iterPos, false */);
		// navMeshQuery.ClosestPointOnPoly(smoothPolys[smoothPolyCount - 1], endPos,
		// targetPos, false);
		iterPos = cpopr.result.getClosest();
		targetPos = endPos;

		float StepSize = 0.5f;
		float Slop = 0.05f;

		int smoothPathNum = 0;
		System.arraycopy(iterPos, 0, smoothPath, smoothPathNum * 3, 3);
		smoothPathNum++;

		/*
		 * for (int i = 0; i < smoothPolyCount; i++) {
		 * navMeshQuery.ClosestPointOnPoly(smoothPolys[i], startPos, iterPos, false);
		 * System.arraycopy(iterPos, 0, smoothPath, i * 3, 3); smoothPathNum++; }
		 */

		if (smoothPolyCount == 2) {
			if (Log.loggingTrace)
				Log.trace("smoothPolyCount == 2");
			// Try send the direct path
			ArrayList<AOVector> pathPoints = new ArrayList<AOVector>();
			pathPoints.add(new AOVector(iterPos[0], iterPos[1], iterPos[2]));
			pathPoints.add(new AOVector(targetPos[0], targetPos[1], targetPos[2]));

			return pathPoints;
		}
		float[] steerPos2 = new float[3];

		DetourNumericReturn numericReturn3 = GetSteerTarget(navMeshQuery, iterPos, targetPos, Slop, /* smoothPolys */path2, smoothPolyCount, steerPos2);
		if (Log.loggingTrace)
			Log.trace("PATH: steerTarget = " + numericReturn3.boolValue + " iterPos:" + Arrays.toString(iterPos) + " targetPos:" + Arrays.toString(targetPos) + " Slop:" + Slop
					+ " path2:" + Arrays.toString(path) + " smoothPolyCount:" + smoothPolyCount + " steerPos:" + Arrays.toString(steerPos2));

		// Log.debug("PATH: smoothPolyCount = " + smoothPolyCount);
		// Move towards target a small advancement at a time until target reached or
		// when ran out of memory to store the path.
		while (smoothPolyCount > 0 && smoothPathNum < 2048) {
			// Find location to steer towards.
			float[] steerPos = new float[3];
			DetourNumericReturn numericReturn2 = GetSteerTarget(navMeshQuery, iterPos, targetPos, Slop, /* smoothPolys */path2, smoothPolyCount, steerPos);
			if (Log.loggingTrace)
				Log.trace("PATH: steerTarget = " + numericReturn2.boolValue + " iterPos:" + Arrays.toString(iterPos) + " targetPos:" + Arrays.toString(targetPos) + " Slop:" + Slop
						+ " path2:" + Arrays.toString(path) + " smoothPolyCount:" + smoothPolyCount + " steerPos:" + Arrays.toString(steerPos));

			if (!numericReturn2.boolValue)
				break;
			short steerPosFlag = (short) numericReturn2.intValue;
			long steerPosRef = numericReturn2.longValue;

			if (Log.loggingTrace)
				Log.trace("endOfPath: steerPosFlag:" + steerPosFlag + " & StraightPathEnd:" + StraightPathEnd + ") != 0" + ((steerPosFlag & StraightPathEnd) != 0));
			boolean endOfPath = (steerPosFlag & StraightPathEnd) != 0;
			if (Log.loggingTrace)
				Log.trace("offMeshConnection: steerPosFlag:" + steerPosFlag + " & StraightPathOffMeshConnection:" + StraightPathOffMeshConnection + ") != 0"
						+ ((steerPosFlag & StraightPathOffMeshConnection) != 0));
			boolean offMeshConnection = (steerPosFlag & StraightPathOffMeshConnection) != 0;
			if (Log.loggingTrace)
				Log.trace("PATH: steerPosFlag:" + steerPosFlag + " steerPosRef:" + steerPosRef + " endOfPath:" + endOfPath + " offMeshConnection:" + offMeshConnection);

			// Find movement delta.
			float[] delta = Helper.VSub(steerPos[0], steerPos[1], steerPos[2], iterPos[0], iterPos[1], iterPos[2]);
			if (Log.loggingTrace)
				Log.trace("PATH: delta:" + Arrays.toString(delta) + " steerPos:" + Arrays.toString(steerPos) + " iterPos:" + Arrays.toString(iterPos));
			float len = (float) Math.sqrt(Helper.VDot(delta, delta));
			if (Log.loggingTrace)
				Log.trace("PATH: len:" + len);
			if (Log.loggingTrace)
				Log.trace(" endOfPath:" + endOfPath + " offMeshConnection:" + offMeshConnection + " len:" + len + " < StepSize:" + StepSize);
			// If the steer target is end of path or off-mesh link, do not move past the
			// location.
			if ((endOfPath || offMeshConnection) && len < StepSize)
				len = 2;
			else {
				len = StepSize / len;
			}
			if (Log.loggingTrace)
				Log.trace("PATH: len:" + len);

			float[] moveTarget = new float[3];
			Helper.VMad(moveTarget, iterPos, delta, len);
			if (Log.loggingTrace)
				Log.trace("moveTarget:" + Arrays.toString(moveTarget) + "iterPos:" + Arrays.toString(iterPos) + " delta:" + Arrays.toString(delta) + " len:" + len);
			// Move
			float[] result = new float[3];
			// long[] visited = new long[16];

			// statusReturn
			if (Log.loggingTrace)
				Log.trace("GeneratePath iterPos:" + Arrays.toString(iterPos) + " moveTarget:" + Arrays.toString(moveTarget));
			Result<MoveAlongSurfaceResult> VisitedstatusReturn = navMeshQuery.moveAlongSurface(smoothPolys[0], iterPos, moveTarget, filter/* , result, visited, 16 */);
			int nVisited = VisitedstatusReturn.result.getVisited().size();
			result = VisitedstatusReturn.result.getResultPos();
			// Log.error("GeneratePath VisitedstatusReturn
			// getResultPos:"+Arrays.toString(result));
			int iii = 0;
			long[] visited = new long[nVisited];
			for (Long ll : VisitedstatusReturn.result.getVisited())
				visited[iii++] = ll;
			// Log.error("GeneratePath VisitedstatusReturn
			// visited:"+Arrays.toString(visited));

			// visited = VisitedstatusReturn.getVisited();
			// smoothPolyCount = FixupCorridor(smoothPolys, smoothPolyCount, maxPolys,
			// visited, nVisited);
			// smoothPolyCount = FixupShortcuts(smoothPolys, smoothPolyCount, navMeshQuery);

			// numericReturn = navMeshQuery.getPolyHeight(smoothPolys[0], result);
			// float h = numericReturn.floatValue;
			// Zb wylaczone
			// float h = navMeshQuery.getPolyHeight(smoothPolys[0], result);
			// result[1] = h;
			System.arraycopy(result, 0, iterPos, 0, 3);

			// Handle end of path and off-mesh links when close enough.
			if (Log.loggingTrace)
				Log.trace("offMeshConnection:" + offMeshConnection + " endOfPath:" + endOfPath + " InRange" + InRange(iterPos, steerPos, Slop, 1.0f));
			if (endOfPath && InRange(iterPos, steerPos, Slop, 1.0f)) {
				if (Log.loggingTrace)
					Log.trace("endOfPath && InRange");
				// Reached end of path.
				System.arraycopy(targetPos, 0, iterPos, 0, 3);
				if (smoothPathNum < 2048) {
					System.arraycopy(iterPos, 0, smoothPath, smoothPathNum * 3, 3);
					smoothPathNum++;
				}
				break;
			} else if (offMeshConnection && InRange(iterPos, steerPos, Slop, 1.0f)) {
				if (Log.loggingTrace)
					Log.trace("offMeshConnection && InRange");

				// Reached off-mesh connection.
				float[] startPosOffMesh = new float[3], endPosOffMesh = new float[3];

				// Advance the path up to and over the off-mesh connection.
				long prevRef = 0, polyRef = smoothPolys[0];
				int npos = 0;
				while (npos < smoothPolyCount && polyRef != steerPosRef) {
					prevRef = polyRef;
					polyRef = smoothPolys[npos];
					npos++;
				}
				for (int i = npos; i < smoothPolyCount; i++) {
					smoothPolys[i - npos] = smoothPolys[i];
				}
				smoothPolyCount -= npos;

				// Handle the connection.
				// EnumSet<Status> status =
				// navMeshQuery.getAttachedNavMesh().getOffMeshConnectionPolyEndPoints(prevRef,
				// polyRef);//, startPosOffMesh, endPosOffMesh);
				Result<Tupple2<float[], float[]>> meshConnectionPolyEndPoints = navMeshQuery.getAttachedNavMesh().getOffMeshConnectionPolyEndPoints(prevRef, polyRef);// , startPosOffMesh,
																																								// endPosOffMesh);

				// if (status.contains(Status.SUCCSESS))
				if (meshConnectionPolyEndPoints != null) {
					if (smoothPathNum < MaxSmooth) {
						System.arraycopy(startPosOffMesh, 0, smoothPath, smoothPathNum * 3, 3);
						smoothPathNum++;
						// Hack to make the dotted path not visible during off-mesh connection.
						if ((smoothPathNum & 1) == 1) {
							System.arraycopy(startPosOffMesh, 0, smoothPath, smoothPathNum * 3, 3);
							smoothPathNum++;
						}
					}
					// Move position at the other side of the off-mesh link.
					System.arraycopy(endPosOffMesh, 0, iterPos, 0, 3);
					// numericReturn = navMeshQuery.getPolyHeight(smoothPolys[0], iterPos);

					// iterPos[1] = numericReturn.floatValue;
					//iterPos[1] 
					Result<Float> rf = navMeshQuery.getPolyHeight(smoothPolys[0], iterPos);
					if(rf.succeeded()) {
						iterPos[1] = rf.result;
					}
				}
			} else {
				if (Log.loggingTrace)
					Log.trace("Else brak endOfPath && InRange");
			}

			// Store results.
			if (smoothPathNum < 2048) {
				System.arraycopy(iterPos, 0, smoothPath, smoothPathNum * 3, 3);
				smoothPathNum++;
			}
		}

		// Put path together
		if (Log.loggingTrace)
			Log.trace("PATH: num smooth path: " + smoothPathNum);// debug
		ArrayList<AOVector> pathPoints = new ArrayList<AOVector>();
		AOVector lastDirection = AOVector.Zero;
		AOVector prev = new AOVector(smoothPath[0], smoothPath[1], smoothPath[2]);
		for (int i = 1; i < smoothPathNum; i++) {
			AOVector currentPoint = new AOVector(smoothPath[i * 3 + 0], smoothPath[i * 3 + 1] + 0.1f, smoothPath[i * 3 + 2]);
			AOVector currentDirection = AOVector.sub(currentPoint, prev).normalize();
			float dif = AOVector.sub(currentDirection, lastDirection).length();
			prev = currentPoint;
			lastDirection = currentDirection;

			if (Math.abs(dif) > 0.01f) {
				pathPoints.add(new AOVector(smoothPath[i * 3 - 3], smoothPath[i * 3 - 2], smoothPath[i * 3 - 1]));
			}
			// Log.debug("PATH: adding smooth path: " + i);
		}
		pathPoints.add(new AOVector(smoothPath[smoothPathNum * 3 - 3], smoothPath[smoothPathNum * 3 - 2], smoothPath[smoothPathNum * 3 - 1]));
		// HACK - remove second point
		if (pathPoints.size() > 3) {
			pathPoints.remove(1);
		}

		/*
		 * for (int i = 1; i < smoothPathNum; i++) { pathPoints.add(new
		 * AOVector(smoothPath[i * 3 + 0], smoothPath[i * 3 + 1], smoothPath[i * 3 +
		 * 2])); //Log.debug("PATH: adding smooth path: " + i); }
		 */
		if (Log.loggingTrace)
			Log.trace("GeneratePath PATH: num smooth path: " + pathPoints);// debug

		return pathPoints;
	}

	private int FixupCorridor(List<Long> path, int npath, int maxPath, List<Long> visited, int nVisited) {
		int furthestPath = -1;
		int furthestVisited = -1;

		// Find furthest common polygon.
		for (int i = npath - 1; i >= 0; --i) {
			boolean found = false;
			for (int j = nVisited - 1; j >= 0; --j) {
				if (path.get(i) == visited.get(j)) {
					furthestPath = i;
					furthestVisited = j;
					found = true;
				}
			}
			if (found)
				break;
		}

		// If no intersection found just return current path.
		if (furthestPath == -1 || furthestVisited == -1)
			return npath;

		// Concatenate paths.

		// Adjust beginning of the buffer to include the visited.
		int req = nVisited - furthestVisited;
		int orig = Math.min(furthestPath + 1, npath);
		int size = Math.max(0, npath - orig);
		if (req + size > maxPath)
			size = maxPath - req;
		if (size > 0)
			System.arraycopy(path, orig, path, req, size);

		// Store visited
		for (int i = 0; i < req; i++) {
			path.set(i, visited.get((nVisited - 1) - i));
		}

		return req + size;
	}

	private int FixupCorridor(long[] path, int npath, int maxPath, long[] visited, int nVisited) {
		int furthestPath = -1;
		int furthestVisited = -1;

		// Find furthest common polygon.
		for (int i = npath - 1; i >= 0; --i) {
			boolean found = false;
			for (int j = nVisited - 1; j >= 0; --j) {
				if (path[i] == visited[j]) {
					furthestPath = i;
					furthestVisited = j;
					found = true;
				}
			}
			if (found)
				break;
		}

		// If no intersection found just return current path.
		if (furthestPath == -1 || furthestVisited == -1)
			return npath;

		// Concatenate paths.

		// Adjust beginning of the buffer to include the visited.
		int req = nVisited - furthestVisited;
		int orig = Math.min(furthestPath + 1, npath);
		int size = Math.max(0, npath - orig);
		if (req + size > maxPath)
			size = maxPath - req;
		if (size > 0)
			System.arraycopy(path, orig, path, req, size);

		// Store visited
		for (int i = 0; i < req; i++) {
			path[i] = visited[(nVisited - 1) - i];
		}

		return req + size;
	}

	// This function checks if the path has a small U-turn, that is,
	// a polygon further in the path is adjacent to the first polygon
	// in the path. If that happens, a shortcut is taken.
	// This can happen if the target (T) location is at tile boundary,
	// and we're (S) approaching it parallel to the tile edge.
	// The choice at the vertex can be arbitrary,
	// +---+---+
	// |:::|:::|
	// +-S-+-T-+
	// |:::| | <-- the step can end up in here, resulting U-turn path.
	// +---+---+
	private int FixupShortcuts(List<Long> path, int npath, NavMeshQuery navQuery) {
		if (npath < 3)
			return npath;

		// Get connected polygons
		int maxNeis = 16;
		long[] neis = new long[maxNeis];
		int nneis = 0;

		// DetourMeshTileAndPoly tileAndPoly =
		// navQuery.getAttachedNavMesh().getTileAndPolyByRef(path[0]);
		Result<Tupple2<MeshTile, Poly>> tileAndPoly = navQuery.getAttachedNavMesh().getTileAndPolyByRef(path.get(0));
		/*
		 * if (tileAndPoly.status.contains(Status.FAILURE)) return npath;
		 */
		MeshTile tile = tileAndPoly.result.first;
		Poly poly = tileAndPoly.result.second;
		if (Log.loggingTrace)
			Log.trace("FixupShortcuts poly.firstLink:" + tile.polyLinks[poly.index]);
		for (long k = tile.polyLinks[poly.index]; k != NavMesh.DT_NULL_LINK; k = tile.links.get((int) k).next) {
			Link link = tile.links.get((int) k);
			if (link.ref != 0) {
				if (nneis < maxNeis)
					neis[nneis++] = link.ref;
			}
		}

		// If any of the neighbour polygons is within the next few polygons
		// in the path, short cut to that polygon directly.
		int maxLookAhead = 6;
		int cut = 0;
		for (int i = Math.min(maxLookAhead, npath) - 1; i > 1 && cut == 0; i--) {
			for (int j = 0; j < nneis; j++) {
				if (path.get(i) == neis[j]) {
					cut = i;
					break;
				}
			}
		}
		if (cut > 1) {
			int offset = cut - 1;
			npath -= offset;
			for (int i = 1; i < npath; i++)
				path.set(i, path.get(i + offset));
		}

		return npath;
	}

	private int FixupShortcuts(long[] path, int npath, NavMeshQuery navQuery) {
		if (npath < 3)
			return npath;

		// Get connected polygons
		int maxNeis = 16;
		long[] neis = new long[maxNeis];
		int nneis = 0;

		// DetourMeshTileAndPoly tileAndPoly =
		// navQuery.getAttachedNavMesh().getTileAndPolyByRef(path[0]);
		Result<Tupple2<MeshTile, Poly>> tileAndPoly = navQuery.getAttachedNavMesh().getTileAndPolyByRef(path[0]);
		/*
		 * if (tileAndPoly.status.contains(Status.FAILURE)) return npath;
		 */
		MeshTile tile = tileAndPoly.result.first;
		Poly poly = tileAndPoly.result.second;
		if (Log.loggingTrace)
			Log.trace("FixupShortcuts poly.firstLink:" + tile.polyLinks[poly.index]);
		for (long k = tile.polyLinks[poly.index]; k != NavMesh.DT_NULL_LINK; k = tile.links.get((int) k).next) {
			Link link = tile.links.get((int) k);
			if (link.ref != 0) {
				if (nneis < maxNeis)
					neis[nneis++] = link.ref;
			}
		}

		// If any of the neighbour polygons is within the next few polygons
		// in the path, short cut to that polygon directly.
		int maxLookAhead = 6;
		int cut = 0;
		for (int i = Math.min(maxLookAhead, npath) - 1; i > 1 && cut == 0; i--) {
			for (int j = 0; j < nneis; j++) {
				if (path[i] == neis[j]) {
					cut = i;
					break;
				}
			}
		}
		if (cut > 1) {
			int offset = cut - 1;
			npath -= offset;
			for (int i = 1; i < npath; i++)
				path[i] = path[i + offset];
		}

		return npath;
	}

	/// <summary>
	/// Helper class for using an array instead of splitting out v1
	/// </summary>
	/// <param name="v1">array of 3 points for vector 1</param>
	/// <param name="v2">array of 3 points for vector 2</param>
	/// <param name="r">radius around which to search</param>
	/// <param name="h">height above or below to check for</param>
	/// <returns></returns>
	private boolean InRange(float[] v1, float[] v2, float r, float h) {
		return InRange(v1[0], v1[1], v1[2], v2, r, h);
	}

	/// <summary>
	/// Checking if V1 is in range of V2
	/// </summary>
	/// <param name="v1x">V1 x-component</param>
	/// <param name="v1y">V1 y-component</param>
	/// <param name="v1z">V1 z-component</param>
	/// <param name="v2">Vector to check with</param>
	/// <param name="r">radius around v1 to check</param>
	/// <param name="h">height above and below v1 to check</param>
	/// <returns></returns>
	private boolean InRange(float v1x, float v1y, float v1z, float[] v2, float r, float h) {
		float dx = v2[0] - v1x;
		float dy = v2[1] - v1y;
		float dz = v2[2] - v1z;
		return (dx * dx + dz * dz) < r * r && Math.abs(dy) < h;
	}

	/// <summary>
	/// Tries to find the straightest path between 2 polygons
	/// </summary>
	/// <param name="navMeshQuery"></param>
	/// <param name="startPos"></param>
	/// <param name="endPos"></param>
	/// <param name="minTargetDistance"></param>
	/// <param name="path"></param>
	/// <param name="pathSize"></param>
	/// <param name="steerPos"></param>
	/// <param name="steerPosFlag"></param>
	/// <param name="steerPosRef"></param>
	/// <param name="outPoints"></param>
	/// <param name="outPointCount"></param>
	/// <returns></returns>

	private DetourNumericReturn GetSteerTarget(NavMeshQuery navMeshQuery, float[] startPos, float[] endPos, float minTargetDistance, List<Long> path,
			int pathSize) {
		// Find steer target.
		DetourNumericReturn returnSteerTarget = new DetourNumericReturn();
		int MaxSteerPoints = 3;
		float[] steerPath = new float[MaxSteerPoints * 3];
		short[] steerPathFlags = new short[MaxSteerPoints];
		long[] steerPathPolys = new long[MaxSteerPoints];
		returnSteerTarget.floatArrayValue = new float[3];
		int nSteerPath = 0;
		//float[] outPoints = null;
		int outPointCount = 0;
		Result<List<StraightPathItem>> spi = navMeshQuery.findStraightPath(startPos, endPos, path, MaxSteerPoints, 0);
		// float[] steerPath
		// short[] steerPathFlags
		// long[] steerPathPolys
		int ii = 0;
		for (StraightPathItem sp : spi.result) {
			System.arraycopy(sp.getPos(), 0, steerPath, ii * 3, 3);
			// Log.error("StraightPathItem "+spi.getFlags());
			steerPathFlags[ii] = (short) sp.getFlags();
			steerPathPolys[ii] = sp.getRef();
			ii++;
		}
		nSteerPath = spi.result.size();
		if (nSteerPath == 0) {
			returnSteerTarget.boolValue = false;
			return returnSteerTarget;
		}

//		if (outPoints != null && outPointCount > 0) {
//			outPointCount = nSteerPath;
//			for (int i = 0; i < nSteerPath; i++) {
//				System.arraycopy(steerPath, i * 3, outPoints, i * 3, 3);
//			}
//		}

		// Find vertex far enough to steer to.
		int ns = 0;
		while (ns < nSteerPath) {
			// Stop at Off-Mesh link or when point is further than slop away.
			if ((steerPathFlags[ns] & StraightPathOffMeshConnection) != 0
					|| !InRange(steerPath[ns * 3 + 0], steerPath[ns * 3 + 1], steerPath[ns * 3 + 2], startPos, minTargetDistance, 1000.0f))
				break;
			ns++;
		}

		// Failed to find good point to steer to.
		if (ns >= nSteerPath) {
			returnSteerTarget.boolValue = false;
			return returnSteerTarget;
		}
		System.arraycopy(steerPath, ns * 3, returnSteerTarget.floatArrayValue, 0, 3);
		returnSteerTarget.floatArrayValue[1] = startPos[1];
		returnSteerTarget.intValue = steerPathFlags[ns];
		returnSteerTarget.longValue = steerPathPolys[ns];
		returnSteerTarget.boolValue = true;
		return returnSteerTarget;
	}

	public static List<Long> asList(final long[] l) {
		return new AbstractList<Long>() {
			public Long get(int i) {
				return l[i];
			}

			// throws NPE if val == null
			public Long set(int i, Long val) {
				Long oldVal = l[i];
				l[i] = val;
				return oldVal;
			}

			public int size() {
				return l.length;
			}
		};
	}

	private DetourNumericReturn GetSteerTarget(NavMeshQuery navMeshQuery, float[] startPos, float[] endPos, float minTargetDistance, List<Long> path, int pathSize,
			float[] steerPos) {
		// Find steer target.
		DetourNumericReturn numericReturn = new DetourNumericReturn();
		int MaxSteerPoints = 3;
		// float[] steerPath = new float[MaxSteerPoints * 3];
		// short[] steerPathFlags = new short[MaxSteerPoints];
		// long[] steerPathPolys = new long[MaxSteerPoints];
		List<Long> pathLongList = path;
		// asList(path);
		// Log.error("GetSteerTarget path:"+Arrays.toString(path)+" -
		// pathLongList:"+pathLongList);

		// Log.error("GetSteerTarget navMeshQuery:"+navMeshQuery+"
		// startPos:"+Arrays.toString(startPos)+" endPos:"+Arrays.toString(endPos)+"
		// pathLongList:"+pathLongList+" MaxSteerPoints:"+MaxSteerPoints);
		Result<List<StraightPathItem>> statusReturn = navMeshQuery.findStraightPath(startPos, endPos, pathLongList, /*
																											 * pathSize, steerPath, steerPathFlags,steerPathPolys,
																											 */MaxSteerPoints, 0);
		if (Log.loggingTrace)
			Log.trace("GetSteerTarget: statusReturn:" + statusReturn.result);
		int nSteerPath = statusReturn.result.size();
		int iii = 0;
		float[] steerPath = new float[nSteerPath * 3];
		short[] steerPathFlags = new short[nSteerPath];
		long[] steerPathPolys = new long[nSteerPath];
		for (StraightPathItem spi : statusReturn.result) {
			System.arraycopy(spi.getPos(), 0, steerPath, iii * 3, 3);

			if (Log.loggingTrace)
				Log.trace("StraightPathItem " + spi.getFlags());

			steerPathFlags[iii] = (short) spi.getFlags();
			steerPathPolys[iii] = spi.getRef();
		}
		if (nSteerPath == 0) {
			numericReturn.boolValue = false;
			return numericReturn;
		}

		// Find vertex far enough to steer to.
		int ns = 0;
		while (ns < nSteerPath) {
			// Stop at Off-Mesh link or when point is further than slop away.
			if ((steerPathFlags[ns] & StraightPathOffMeshConnection) != 0
					|| !InRange(steerPath[ns * 3 + 0], steerPath[ns * 3 + 1], steerPath[ns * 3 + 2], startPos, minTargetDistance, 1000.0f))
				break;
			ns++;
		}

		// Failed to find good point to steer to.
		if (ns >= nSteerPath) {
			numericReturn.boolValue = false;
			return numericReturn;
		}

		System.arraycopy(steerPath, ns * 3, steerPos, 0, 3);
		steerPos[1] = startPos[1];
		numericReturn.intValue = steerPathFlags[ns];
		numericReturn.longValue = steerPathPolys[ns];

		numericReturn.boolValue = true;
		return numericReturn;
	}

	public OID getInstanceOid() {
		return instanceOid;
	}
	
	 private ConcurrentLinkedQueue<AtavismBuildingObject> dynamicObjectQueue = new ConcurrentLinkedQueue<AtavismBuildingObject>();
	 private ConcurrentLinkedQueue<Integer> dynamicObjectDelQueue = new ConcurrentLinkedQueue<Integer>();
	 private ConcurrentHashMap<Integer,AtavismBuildingObject> dynamicObjectSpawned = new ConcurrentHashMap<Integer,AtavismBuildingObject>();

	
	private ArrayList<DetourActor> actors = new ArrayList<DetourActor>();
	private ArrayList<DetourActor> actorsToAdd = new ArrayList<DetourActor>();
	private ArrayList<DetourActor> actorsToRemove = new ArrayList<DetourActor>();

	transient protected Lock lock = null;
	
	private String instanceName="";
	private OID instanceOid;
	private NavMesh navMesh;// = new NavMesh();
	private QueryFilter filter;
	private Crowd crowd;
	private long lastUpdate;
	private DynamicNavMesh dynamicNavMesh=null;

	private static final int StraightPathEnd = 2;
	private static final int StraightPathOffMeshConnection = 4;
	private static final int MaxSmooth = 2048;
}