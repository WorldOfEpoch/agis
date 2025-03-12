package atavism.agis.objects;

import atavism.server.util.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.locks.*;

public class AgisAttachSocket implements Serializable {
    public AgisAttachSocket() {
    }

    public AgisAttachSocket(String socketName) {
	this.name = socketName;
	mapLock.lock();
	try {
	    socketNameMapping.put(socketName, this);
	}
	finally {
	    mapLock.unlock();
	}
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
	return name;
    }
    private String name;

    public String toString() {
	return "[AgisAttachSocket name=" + getName() + "]";
    }

    public static AgisAttachSocket getSocketByName(String socketName) {
	mapLock.lock();
	try {
	    return socketNameMapping.get(socketName);
	}
	finally {
	    mapLock.unlock();
	}
    }

    private static Map<String, AgisAttachSocket> socketNameMapping =
	new HashMap<String, AgisAttachSocket>();


    private static Lock mapLock = LockFactory.makeLock("AgisAttachSocketLock");

    public static AgisAttachSocket PRIMARYWEAPON = 
    	new AgisAttachSocket("primaryWeapon");
    public static AgisAttachSocket HEAD = 
        	new AgisAttachSocket("head");
    public static AgisAttachSocket LSHOULDER = 
        	new AgisAttachSocket("shoulderL");
    public static AgisAttachSocket RSHOULDER = 
        	new AgisAttachSocket("shoulderR");
    public static AgisAttachSocket SECONDARYWEAPON = 
        	new AgisAttachSocket("secondaryWeapon");
    public static AgisAttachSocket SHIELD = 
        	new AgisAttachSocket("shield");
    public static AgisAttachSocket BACK = 
        	new AgisAttachSocket("back");
    public static AgisAttachSocket PRIMARYWEAPONIDLE = 
    	new AgisAttachSocket("primaryWeaponIdle");
    public static AgisAttachSocket SECONDARYWEAPONIDLE = 
    	new AgisAttachSocket("secondaryWeaponIdle");

    private static final long serialVersionUID = 1L;
}