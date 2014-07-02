package com.opentrons.otbtalpha.cordova;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.opentrons.otbtalpha.cordova.OTBTLogicAlpha.ExecuteResult;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;


public class OTBTServiceAlpha extends Service implements IUpdateListener{
	
	/*
	 ************************************************************************************************
	 * Static values 
	 ************************************************************************************************
	 */
	public static final String TAG = OTBTServiceAlpha.class.getSimpleName();
	private static final boolean D = true;
	// Message types sent from the BluetoothSerialService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
    
    public static String args;
    public static JSONObject job;
    public static JSONArray ingredients;
    public static String address;
    
	
    StringBuffer buffer = new StringBuffer();
    
    private OTBTApiAlpha mApi;
    private Object mServiceConnectedLock = new Object();
	private Boolean mServiceConnected = null;
    
    
    private boolean mServiceInitialised = false;
	private boolean boomthreading = false;
	private final Object mResultLock = new Object();
	private JSONObject mLatestResult = null;
	
	private List<OTBTListenerAlpha> mListeners = new ArrayList<OTBTListenerAlpha>();
	
	public static Handler dHandler = new Handler();
	
	protected JSONObject getLatestResult() {
		synchronized (mResultLock) {
			return mLatestResult;
		}
	}
	
	protected void setLatestResult(JSONObject value) {
		synchronized (mResultLock) {
			this.mLatestResult = value;
		}
	}
	
	
	
	/*
	 ************************************************************************************************
	 * Overriden Methods 
	 ************************************************************************************************
	 */
	
	@Override  
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind called");
		return apiEndpoint;
	}     
	
	@Override  
	public void onCreate() {     
		super.onCreate();     
		Log.i(TAG, "Service creating");
		if(!mServiceInitialised) {
			final ActivityManager activityManager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
			final List<RunningServiceInfo> services = 
					activityManager.getRunningServices(Integer.MAX_VALUE);
			for (int i = 0; i < services.size(); i++){
				if(D){
					Log.d(TAG,"SERVICES_A|Service Nr. " + i + ":" + services.get(i).service);
					Log.d(TAG,"SERVICES_B|Service Nr. " + i + " package name : " + services.get(i).service.getPackageName());
					Log.d(TAG,"SERVICES_C|Service Nr. " + i + " class name : " + services.get(i).service.getClassName());
				}
				if(services.get(i).service.getClassName().equals("com.opentrons.otbtalpha.cordova.OTBTBlueServiceAlpha")){
					Intent intent = new Intent("com.opentrons.otbtalpha.cordova.OTBTBlueServiceAlpha");
					Log.d(TAG, "Attempting to bind to BLUE");
					if (this.getApplicationContext().bindService(intent, serviceConnection, 0)) {
						Log.d(TAG, "Waiting for service connected lock");
						synchronized(mServiceConnectedLock) {
							while (mServiceConnected==null) {
								try {
									mServiceConnectedLock.wait();
								} catch (InterruptedException e) {
									Log.d(TAG, "Interrupt occurred while waiting for connection", e);
								}
							}
							//result = this.mServiceConnected;
						}
					}
				}
			}
		}
		// Duplicating the call to initialiseService across onCreate and onStart
		// Done this to ensure that my initialisation code is called.
		// Found that the onStart was not called if Android was re-starting the service if killed
		initialiseService();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		Log.i(TAG, "Service started");       
		
		// Duplicating the call to initialiseService across onCreate and onStart
		// Done this to ensure that my initialisation code is called.
		// Found that the onStart was not called if Android was re-starting the service if killed
		initialiseService();
		args = intent.getStringExtra("args");
		address = intent.getStringExtra("address");
		Log.d(TAG, "args: " + args);
		Log.d(TAG, "address: " + address);
		try {
			if(!boomthreading){
				job = new JSONObject(args);
				BoomThread boomer = new BoomThread(job, address);
				dHandler.postDelayed(boomer, 1000);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	@Override  
	public void onDestroy() {     
		super.onDestroy();     
		Log.i(TAG, "Service destroying");
		
		
	}
	
	
	/*
	 ************************************************************************************************
	 * Private methods 
	 ************************************************************************************************
	 */
	private JSONObject getConfig() throws JSONException{
		return job;
	}
	
	private void setConfig(JSONObject joob) throws JSONException {
		job = joob;
	}
	
	
	private OTBTApiAlpha.Stub apiEndpoint = new OTBTApiAlpha.Stub() {
		
		/*
		 ************************************************************************************************
		 * Overriden Methods 
		 ************************************************************************************************
		 */
		@Override
		public String getLatestResult() throws RemoteException {
			synchronized (mResultLock) {
				if (mLatestResult == null)
					return "{}";
				else
					return mLatestResult.toString();
			}
		}
		
		@Override
		public void addListener(OTBTListenerAlpha listener)
				throws RemoteException {
			
			synchronized (mListeners) {
				if (mListeners.add(listener))
					Log.d(TAG, "Listener added");
				else
					Log.d(TAG, "Listener not added");
			}
		}
		
		@Override
		public void removeListener(OTBTListenerAlpha listener) throws RemoteException {
			
			synchronized (mListeners) {
				if (mListeners.size() > 0) {
					boolean removed = false;
					for (int i = 0; i < mListeners.size() && !removed; i++)
					{
						if (listener.getUniqueID().equals(mListeners.get(i).getUniqueID())) {
							mListeners.remove(i);
							removed = true;
						}
					}
					
					if (removed)
						Log.d(TAG, "Listener removed");
					else 
						Log.d(TAG, "Listener not found");
				}
			}
		}
		
		
		
		@Override
		public String getConfiguration() throws RemoteException {
			try {
				JSONObject argh;
				argh = getConfig();
				if (argh == null)
					return "";
				else 
					return argh.toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return "";
		}
		/*
		@Override
		public void setConfiguration(String configuration) throws RemoteException {
			try {
				JSONObject array = null;
				if (configuration.length() > 0) {
					array = new JSONObject(configuration);
				} else {
					array = new JSONObject();
				}	
				setConfig(array);
			} catch (Exception ex) {
				throw new RemoteException();
			}
		}*/

		@Override
		public void run(String configuration) throws RemoteException {
			try{
				JSONObject argh = null;
				if(configuration.length()>0){
					argh = new JSONObject(configuration);
					setConfig(argh);
					runOnce();
				}
			}catch(Exception ex){
				throw new RemoteException();
			}
		}

		@Override
		public void pause() throws RemoteException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void kill() throws RemoteException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void write(byte[] data) throws RemoteException {
			// NOOP
		}

		@Override
		public void connect(String address) throws RemoteException {
			// NOOP
		}

		@Override
		public int getState() throws RemoteException {
			return 0; // basically NOOP
		}

		@Override
		public void stop() throws RemoteException {
			// NOOP
		}

		@Override
		public void disconnect() throws RemoteException {
			// NOOP
			
		}
	};

	private void initialiseService() {
		
		if (!this.mServiceInitialised) {
			Log.i(TAG, "Initialising the service");

						
			this.mServiceInitialised = true;
		}

	}
	
	/*
	*/
   
   /*private void notifyConnectionLost(String error){
//	   Log.i(TAG, "notifying to all listeners of msg");
	   for (int i = 0; i < mListeners.size(); i++)
	   {
		   try {
			   mListeners.get(i).handleUpdate();
			   mListeners.get(i).notifyError(error);
		   } catch (RemoteException e) {
				Log.i(TAG, "Failed to notify listener - " + i + " - " + e.getMessage());
		   }
	   }
   }*/
   
   
   private void runOnce(){
	  // NOOP 
   }
   
   
   private class BoomThread implements Runnable {
	   
	   private JSONObject mJob;
	   private JSONArray mIngredients;
	   private JSONArray mProtocol;
	   //private OTBTWorkerAlpha whack;
	   private String mAddress;
	   private String mMessage = "";
	   private BlockingQueue<String> whackattack = new LinkedBlockingQueue<String>();
	   private HashMap<String, Location> hIngredients;
	   private int pipette;
	   private int ji = 0;
	   private int idx = 0;
	   private double adiff = 0.0;
	   private double bdiff = 0.0;
	   private double bopen = 0.0;
	   private double bclose = 0.0;
	   private double ablow = 0.0;
	   
	   private boolean proceed = true;
	   private boolean running = true;
	   private boolean firstrun = true;
	   
	   public BoomThread(JSONObject job, String address) {
		   mJob = job;
		   mAddress = address;
		   try {
			   pipette = job.getInt("pipette");
			   Log.d(TAG, "pipette = "+String.valueOf(pipette));
		   } catch (JSONException e1) {
			   // TODO Auto-generated catch block
			    e1.printStackTrace();
		   }
		   try {
			   mIngredients = job.getJSONArray("ingredients");
			   Log.d(TAG, "ingredients = "+mIngredients.toString());
		   } catch (JSONException e) {
			   // TODO Auto-generated catch block
			   e.printStackTrace();
		   }
		   
		   
		   try{
			   for(int i=0; i<mIngredients.length(); i++){
				   Location loco = new Location();
				   JSONObject jay = mIngredients.getJSONObject(i);
				   loco.ingredient = jay.getString("name");
				   loco.x = jay.getDouble("x");
				   loco.y = jay.getDouble("y");
				   loco.z = jay.getDouble("z");
				   hIngredients.put(loco.ingredient, loco);
			   }
		   }catch(Exception ex){
			   
		   }
	   }
	   
	   @Override
	   public void run() {
		   // TODO Auto-generated method stub
		   Log.d(TAG, "run() called");
		   boomerang();
		   Log.d(TAG, "trying to whack some bytes");
		   try {
			   mApi.write("{\"gc\":\"g90 g0 x0y0z0\"}\n".getBytes());
		   } catch (RemoteException e) {
			   // TODO Auto-generated catch block
			   e.printStackTrace();
		   }
		   Log.d(TAG, "running = "+String.valueOf(running));
		   while(running){ /* NOOP */ }
		   boomthreading = false;
		   Log.d(TAG, "run() finished");
	   }
	   
	   private void boomerang() {
		   synchronized(this){
			   Log.d(TAG, "boomerang called");
			   String data = readUntil("\n");
			   String jsonStr = "";
			   if(data != null && data.length() > 0){
		    		try {
		    			Log.d(TAG, "data read = "+data);
		    			JSONObject json = new JSONObject(data);
		    			if (json.has("r")) {
		    				processBody(json.getJSONObject("r"));
		    			}else if (json.has("sr")) {
		    				processStatusReport(json.getJSONObject("sr"));
		    			}
		    			//PluginResult result = new PluginResult(PluginResult.Status.OK, jsonStr);
		                //result.setKeepCallback(true);
		                //dataAvailableCallback.sendPluginResult(result);
		    			if(mMessage!=null&&!mMessage.equals("")) {
		    				// TODO: SEND MESSAGE(JSON) BACK TO UI
		    			}
		    		} catch(Exception e){
		    			if(e.getMessage()!=null)
		    				Log.e(TAG, e.getMessage());
		    			
		    		}
		    		
		    		
		            boomerang();
	            }
		   }
	   }
	   
	   private String readUntil(String c) {
		   String data = "";
		   int index = buffer.indexOf(c, 0);
		   if (index > -1) {
			   data = buffer.substring(0, index + c.length());
			   buffer.delete(0, index + c.length());
		   }
		   return data;
	   }
	   
	   private String processBody(JSONObject json) throws JSONException {
		   Log.d(TAG, "processBody called");
		   String result = "";
		   if(json.has("sr"))
			   result = processStatusReport(json.getJSONObject("sr"));
		   return result;
	   }
	    
	   
	   private String processStatusReport(JSONObject sr) throws JSONException{
		   Log.d(TAG, "processStatusReport called");
		   String result = "";
		   
		   if (sr.has("stat")){
			   if(sr.getInt("stat")==3){
				   if(whackattack.size()>0){
					   try {
						   String round = whackattack.take();
						   mApi.write(round.getBytes());
					   } catch (InterruptedException e) {
						   // TODO Auto-generated catch block
						   e.printStackTrace();
					   } catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					   
				   }else{
					   if(!commandSetup(mJob));
					   		endSequence();
					   
				   }
				   
			   }
		   }
		   
		   
		   
		   /*
		   JSONObject jResult = new JSONObject();
		   if (sr.has("posx")){
			   posx = sr.getDouble("posx");
			   jResult.put("x", posx);
		   }
		   if (sr.has("posy")){
			   posy = sr.getDouble("posy");
			   jResult.put("y", posy);
		   }
		   if (sr.has("posz")){
			   posz = sr.getDouble("posz");
			   jResult.put("z", posz);
		   }
		   if (sr.has("posa")){
			   double t_posa = sr.getDouble("posa");
			   Log.d(TAG, "t_posa = "+String.valueOf(t_posa));
			   posa = t_posa*Math.PI*2.0*rosa/360.0;
			   Log.d(TAG, "posa = "+String.valueOf(posa));
			   if(abc==0){
				   jResult.put("a", posa-a_diff);
			   }else if(abc==1){
				   jResult.put("b", posa-b_diff);
			   }else if(abc==2){
				   jResult.put("c", posa-c_diff);
			   }
			   jResult.put("a", posa);
			   Log.d(TAG, "pos a_diff="+String.valueOf(a_diff));
			   Log.d(TAG, "pos b_diff="+String.valueOf(b_diff));
			   Log.d(TAG, "pos c_diff="+String.valueOf(c_diff));
		   }
		   if (sr.has("stat")){
			   switch (sr.getInt("stat")){
			   case 0:
				   jResult.put("listening", 0);
				   break;
			   case 1:
				   jResult.put("listening", 1);
				   break;
			   case 2:
				   jResult.put("listening", 0);
				   break;
			   case 3:
				   jResult.put("listening", 1);
				   break;
			   case 4:
				   jResult.put("listening", 0);
				   break;
			   case 5:
				   jResult.put("listening", 0);
				   break;
			   case 6:
				   jResult.put("listening", 0);
				   break;
			   case 7:
				   jResult.put("listening", 0);
				   break;
			   case 8:
				   jResult.put("listening", 0);
				   break;
			   case 9:
				   jResult.put("listening", 0);
				   break;
			   }
		   }
		   result = jResult.toString();
		   LOG.d(TAG, "result: "+result);
		   
		   */
		   
		   
		   
		   return result;
	   }
	   
	   public boolean commandSetup(JSONObject json){
		   double time = 0.0;
		   double aspirate = 0.0;
		   int grip = 0;
		   boolean blowout = false;
		   String ingredient = "";
		   
		   
		   try{
			   ingredient = json.getString("ingredient");
		   }catch(Exception iex){
			   return false;
		   }
		   
		   try{
			   time = json.getJSONObject("trigger").getDouble("value");
		   }catch(Exception timex){
		   }
		   try{
			   aspirate = json.getJSONObject("action").getDouble("aspirate");
		   }catch(Exception apex){
		   }
		   try{
			   grip = json.getJSONObject("action").getInt("grip");
		   }catch(Exception gex){
		   }
		   try{
			   json.getJSONObject("action").get("blowout");
			   blowout = true;
		   }catch(Exception bex){
		   }   
		   Log.d(TAG, "ingredient:"+ingredient);
		   Log.d(TAG, "time:"+String.valueOf(time));
		   Log.d(TAG, "aspirate:"+String.valueOf(aspirate));
		   Log.d(TAG, "grip:"+String.valueOf(grip));
		   Log.d(TAG, "blowout:"+String.valueOf(blowout));
			   
		   // 1. Create delay if any
		   if(time>0.0){
			   proceed = false;
			   Delay del = new Delay(time*1000);
			   del.run();
		   }
		   
		   while(!proceed){ /*NOOP*/ }
		   
		   // 2. Create gcode commands for completing action
		   String cmdStr;
		   Location loco = hIngredients.get(ingredient);
		   String xgo = String.valueOf(loco.x);
		   Log.d(TAG, "xgo = " + xgo);
		   String ygo = String.valueOf(loco.y);
		   Log.d(TAG, "ygo = " + ygo);
		   idx++;
		   cmdStr = "N" + idx + " g0X"+xgo+"Y"+ygo+"\n";
		   whackattack.add(cmdStr);
		   
		   String zgo = String.valueOf(loco.z);
		   Log.d(TAG, "zgo = " + zgo);
		   idx++;
		   cmdStr = "N" + idx + " g0Z"+zgo+"\n";
		   whackattack.add(cmdStr);
		   
		   String ago = "";
		   switch(pipette){
			   case 0:
				   break;
			   case 1:
				   break;
			   case 2:
				   break;
			   case 3:
				   break;
			   case 4:
				   break;
			   default:
				   break;
		   }
		   if(!ago.equals("")){
			   idx++;
			   cmdStr = "N" + idx + "M5\n";
			   whackattack.add(cmdStr);
			   idx++;
			   cmdStr = "N" + idx + " g0A" + ago + "\n";
			   whackattack.add(cmdStr);
		   }
		   
		   if(grip==0){
			   idx++;
			   cmdStr = "N" + idx + "M3\n";
			   whackattack.add(cmdStr);
			   idx++;
			   cmdStr = "N" + idx + " g0A"+String.valueOf(bopen)+"\n";
			   whackattack.add(cmdStr);
		   }else if(grip==1){
			   idx++;
			   cmdStr = "N" + idx + "M3\n";
			   whackattack.add(cmdStr);
			   idx++;
			   cmdStr = "N" + idx + " g0A"+String.valueOf(bclose)+"\n";
			   whackattack.add(cmdStr);
		   }
		   
		   if(blowout){
			   idx++;
			   cmdStr = "N" + idx + "M5\n";
			   whackattack.add(cmdStr);
			   idx++;
			   cmdStr = "N" + idx + " g0A"+String.valueOf(ablow)+"\n";
			   whackattack.add(cmdStr);
		   }
		   
		   // Z return to 0 at end of job
		   idx++;
		   cmdStr = "N" + idx + "g0Z0\n";
		   whackattack.add(cmdStr);
		   
		   // 3. Add them to whackattack queue (throughout above)
		   
		   return true;
	   }
	   
	   public void endSequence(){
		   
	   }
	   
	   private class Delay implements Runnable{
		   int _delay=0;
		   public Delay(double delay){
			   _delay = (int)delay;
		   }
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				Thread.sleep(_delay);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			proceed = true;
		}
		   
	   }
   }
   
   
   private class Location{
	   public double x, y, z;
	   public String ingredient;
	   Location(){
		   
	   }	
	   
   }
   
   private OTBTListenerAlpha.Stub serviceListener = new OTBTListenerAlpha.Stub() {
		@Override
		public void handleUpdate() throws RemoteException {
			//handleLatestResult();
		}
		
		@Override
		public String getUniqueID() throws RemoteException {
			return "NOOP";
		}
		
		@Override
		public void sendMessage(String data) throws RemoteException {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void notifySuccess() throws RemoteException {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void notifyError(String error) throws RemoteException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void sendBundle(Bundle bunt) throws RemoteException {
			int what = bunt.getInt("what");
			switch (what) {
           case MESSAGE_READ:
              buffer.append(bunt.getString("message"));
              
              //if (dataAvailableCallback != null) {
              //    sendDataToSubscriber();
              //}
              /*
              if(jogDataCallback != null){ //nwags
              	sendJogDataToSubscriber();
              }*/
              break;
           case MESSAGE_STATE_CHANGE:
           	int arg1 = bunt.getInt("state");
              if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + arg1);
              switch (arg1) {
                  case OTBTBlueServiceAlpha.STATE_CONNECTED:
                      Log.i(TAG, "BLUE.STATE_CONNECTED");
                      //notifyConnectionSuccess();
                      break;
                  case OTBTBlueServiceAlpha.STATE_CONNECTING:
                      Log.i(TAG, "BLUE.STATE_CONNECTING");
                      break;
                  case OTBTBlueServiceAlpha.STATE_LISTEN:
                      Log.i(TAG, "BLUE.STATE_LISTEN");
                      break;
                  case OTBTBlueServiceAlpha.STATE_NONE:
                      Log.i(TAG, "BLUE.STATE_NONE");
                      break;
              }
              break;
          case MESSAGE_WRITE:
              //  byte[] writeBuf = (byte[]) msg.obj;
              //  String writeMessage = new String(writeBuf);
              //  Log.i(TAG, "Wrote: " + writeMessage);
              break;
          case MESSAGE_DEVICE_NAME:
              Log.i(TAG, bunt.getString("device_name"));
              break;
          case MESSAGE_TOAST:
              String message = bunt.getString("toast"); 
              Log.d(TAG, "toast = "+message);
              //msg.getData().getString(TOAST);
              //notifyConnectionLost(message);
              break;
			}
		}
	};
   
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// that's how we get the client side of the IPC connection
			mApi = OTBTApiAlpha.Stub.asInterface(service);
			try {
				mApi.addListener(serviceListener);
			} catch (RemoteException e) {
				Log.d(TAG, "addListener failed", e);
			}
			
			synchronized(mServiceConnectedLock) {
				mServiceConnected = true;

				mServiceConnectedLock.notify();
			}

		}
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			synchronized(mServiceConnectedLock) {
				mServiceConnected = false;

				mServiceConnectedLock.notify();
			}
		}
	};

	@Override
	public void handleUpdate(ExecuteResult logicResult, Object[] listenerExtras) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void closeListener(ExecuteResult logicResult, Object[] listenerExtras) {
		// TODO Auto-generated method stub
		
	}
	
}

