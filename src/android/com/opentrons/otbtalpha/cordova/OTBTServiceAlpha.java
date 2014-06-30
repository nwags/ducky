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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;


public class OTBTServiceAlpha extends Service{
	
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
    public static JSONArray job;
    public static JSONArray ingredients;
    
	
    StringBuffer buffer = new StringBuffer();
    
    
    private Boolean mServiceInitialised = false;
	
	private final Object mResultLock = new Object();
	private JSONObject mLatestResult = null;
	
	private List<OTBTListenerAlpha> mListeners = new ArrayList<OTBTListenerAlpha>();
	
	
	
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
		(new Thread(new BoomThread(job, ingredients))).start();
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
	private JSONArray getConfig() throws JSONException{
		JSONArray json = new JSONArray();
		if(job!=null)
			json.put(0, job);
		
		if(ingredients!=null)
			json.put(1, ingredients);
		
		return json;
	}
	
	private void setConfig(JSONArray array) throws JSONException {
		job = array.getJSONArray(0);
		ingredients = array.getJSONArray(1);
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
		public void removeListener(OTBTListenerAlpha listener)
				throws RemoteException {
			
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
				JSONArray array;
				array = getConfig();
				if (array == null)
					return "";
				else 
					return array.toString();
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
				JSONArray array = null;
				if(configuration.length()>0){
					array = new JSONArray(configuration);
					setConfig(array);
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
	};

	private void initialiseService() {
		
		if (!this.mServiceInitialised) {
			Log.i(TAG, "Initialising the service");

						
			this.mServiceInitialised = true;
		}

	}
	
	/*
	*/
   
   private void notifyConnectionLost(String error){
	   Log.i(TAG, "notifying to all listeners of msg");
	   for (int i = 0; i < mListeners.size(); i++)
	   {
		   try {
			   mListeners.get(i).handleUpdate();
			   mListeners.get(i).notifyError(error);
		   } catch (RemoteException e) {
				Log.i(TAG, "Failed to notify listener - " + i + " - " + e.getMessage());
		   }
	   }
   }
   
   private void notifyConnectionSuccess() {
	   Log.i(TAG, "notifying to all listeners of msg");
	   for (int i = 0; i < mListeners.size(); i++)
	   {
		   try {
			   mListeners.get(i).handleUpdate();
			   mListeners.get(i).notifySuccess();
		   } catch (RemoteException e) {
				Log.i(TAG, "Failed to notify listener - " + i + " - " + e.getMessage());
		   }
	   }
   }
   
   private void notifyListeners(String msg){
	   Log.i(TAG, "notifying to all listeners of msg");
	   for (int i = 0; i < mListeners.size(); i++)
	   {
		   try {
			   mListeners.get(i).handleUpdate();
			   mListeners.get(i).sendMessage(msg);
		   } catch (RemoteException e) {
				Log.i(TAG, "Failed to notify listener - " + i + " - " + e.getMessage());
		   }
	   }
   }
   
   
   private void runOnce(){
	   
   }
   
   
   private class BoomThread implements Runnable {
	   
	   private JSONArray mJob;
	   private JSONArray mIngredients;
	   private OTBTWorkerAlpha whack;
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
	   
	   private final Handler mHandler = new Handler() {
		   
	        public void handleMessage(Message msg) {
	            switch (msg.what) {
	                case MESSAGE_READ:
	                   buffer.append((String)msg.obj);
	                   
	                   boomerang();
	                   //if (dataAvailableCallback != null) {
	                   //    sendDataToSubscriber();
	                   //}
	                   // TODO: NOTIFY JOB RUNNING THREAD
	                   /*
	                   if(jogDataCallback != null){ //nwags
	                   	sendJogDataToSubscriber();
	                   }*/
	                   break;
	                case MESSAGE_STATE_CHANGE:

	                   if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
	                   switch (msg.arg1) {
	                       case OTBTWorkerAlpha.STATE_CONNECTED:
	                           Log.i(TAG, "BluetoothSerialService.STATE_CONNECTED");
	                           notifyConnectionSuccess();
	                           break;
	                       case OTBTWorkerAlpha.STATE_CONNECTING:
	                           Log.i(TAG, "BluetoothSerialService.STATE_CONNECTING");
	                           break;
	                       case OTBTWorkerAlpha.STATE_LISTEN:
	                           Log.i(TAG, "BluetoothSerialService.STATE_LISTEN");
	                           break;
	                       case OTBTWorkerAlpha.STATE_NONE:
	                           Log.i(TAG, "BluetoothSerialService.STATE_NONE");
	                           break;
	                   }
	                   break;
	               case MESSAGE_WRITE:
	                   //  byte[] writeBuf = (byte[]) msg.obj;
	                   //  String writeMessage = new String(writeBuf);
	                   //  Log.i(TAG, "Wrote: " + writeMessage);
	                   break;
	               case MESSAGE_DEVICE_NAME:
	                   Log.i(TAG, msg.getData().getString(DEVICE_NAME));
	                   break;
	               case MESSAGE_TOAST:
	                   String message = msg.getData().getString(TOAST);
	                   notifyConnectionLost(message);
	                   break;
	            }
	        }
	   };
	   
	   
	   
	   public BoomThread(JSONArray job, JSONArray ingredients) {
		   mJob = job;
		   mIngredients = ingredients;
		   whack = new OTBTWorkerAlpha(mHandler);
		   
		   try{
			   for(int i=0; i<mIngredients.length(); i++){
				   Location loco = new Location();
				   JSONObject jay = mIngredients.getJSONObject(i);
				   loco.ingredient = jay.getString("name");
				   loco.x = jay.getDouble("x");
				   loco.y = jay.getDouble("y");
				   loco.z = jay.getDouble("z");
				   loco.pipette = jay.getInt("pipette");
				   hIngredients.put(loco.ingredient, loco);
			   }
		   }catch(Exception ex){
			   
		   }
	   }
	   
	   @Override
	   public void run() {
		   // TODO Auto-generated method stub
		   
		   
		   while(running){ /* NOOP */ }
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
		    			if(mMessage!=null&&!mMessage.equals("")){
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
						   whack.write(round.getBytes());
					   } catch (InterruptedException e) {
						   // TODO Auto-generated catch block
						   e.printStackTrace();
					   }
					   
				   }else{
					   if(!commandSetup(mJob.getJSONObject(ji++)));
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
		   StringBuilder ordnance = new StringBuilder();
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
	   public int pipette;
	   Location(){
		   
	   }	
	   
   }
   
   
   
   
}

