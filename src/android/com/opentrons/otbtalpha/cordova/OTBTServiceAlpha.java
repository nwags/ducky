package com.opentrons.otbtalpha.cordova;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
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
	
	
	private BluetoothAdapter bluetoothAdapter;
    private OTBTWorkerAlpha otbtworker;
	
    StringBuffer buffer = new StringBuffer();
    
    
    
    private Boolean mServiceInitialised = false;
	
	private final Object mResultLock = new Object();
	private JSONObject mLatestResult = null;
	
	private List<OTBTListenerAlpha> mListeners = new ArrayList<OTBTListenerAlpha>();
	
	private static String job_configs;
	private static String ingredients;
	
	
	
	
	
	
	
	
	
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
		if(job_configs!=null&& !job_configs.equals(""))
			json.put(0,new JSONArray(job_configs));
		
		if(ingredients!=null&& !ingredients.equals(""))
			json.put(1,new JSONArray(ingredients));
		
		return json;
	}
	
	private void setConfig(JSONArray array) throws JSONException {
		job_configs = array.getJSONArray(0).toString();
		ingredients = array.getJSONArray(1).toString();
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
				// TODO Auto-generated catch block
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
	
	
	private final Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                   buffer.append((String)msg.obj);
                   
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
	   // TODO: DO SOME BOOM-ING HERE, ie start thread to handle job
	   
	   
	   
   }
   
   
   
}

