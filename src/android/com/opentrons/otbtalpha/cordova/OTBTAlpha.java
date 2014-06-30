package com.opentrons.otbtalpha.cordova;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;




// kludgy imports to support 2.9 and 3.0 due to package changes
import org.apache.cordova.*;
import org.apache.cordova.api.*;
// import org.apache.cordova.CordovaArgs;
// import org.apache.cordova.CordovaPlugin;
// import org.apache.cordova.CallbackContext;
// import org.apache.cordova.PluginResult;
// import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.opentrons.otbtalpha.cordova.OTBTLogicAlpha.ExecuteResult;
import com.opentrons.otbtalpha.cordova.OTBTLogicAlpha.ExecuteStatus;

import java.util.Set;

/**
 * Cordova Plugin for Serial Communication with Opentrons over Bluetooth (based off of Don Coleman's
 * BluetoothSerial plugin)
 */

public class OTBTAlpha extends CordovaPlugin implements IUpdateListener {

	/*
	 ************************************************************************************************
	 * Static values 
	 ************************************************************************************************
	 */
	// Debugging
    private static final String TAG = OTBTAlpha.class.getSimpleName();
    private static final boolean D = true;

    
	/*
    // actions
    private static final String LIST = "list";
    private static final String CONNECT = "connect";
    //private static final String CONNECT_INSECURE = "connectInsecure";
    private static final String DISCONNECT = "disconnect";
    //private static final String WRITE = "write";
    private static final String AVAILABLE = "available";
    //private static final String READ = "read";
    //private static final String READ_UNTIL = "readUntil";
    private static final String SUBSCRIBE = "subscribe";
    private static final String UNSUBSCRIBE = "unsubscribe";
    private static final String IS_ENABLED = "isEnabled";
    private static final String IS_CONNECTED = "isConnected";
    private static final String CLEAR = "clear";
    
    //nwags-actions
    private static final String JOG = "jog";
    private static final String SET_DIMENSIONS = "setDimensions";
    private static final String GET_DIMENSIONS = "getDimensions";
    private static final String HOME = "home";
    */
    
    /*
	 ************************************************************************************************
	 * Fields 
	 ************************************************************************************************
	 */
    private OTBTLogicAlpha mLogic = null;
    
    

    /*
	 ************************************************************************************************
	 * Overriden Methods 
	 ************************************************************************************************
	 */
    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
    	
        LOG.d(TAG, "action = " + action);

        boolean result = false;
        
        if(this.mLogic == null)
        	this.mLogic = new OTBTLogicAlpha(this.cordova.getActivity());
        
        try{
        	if(this.mLogic.isActionValid(action)){
        		final IUpdateListener listener = this;
        		final Object[] listenerExtras = new Object[] { callbackContext };
        		
        		cordova.getThreadPool().execute(new Runnable(){
        			@Override
        			public void run() {
        				ExecuteResult logicResult = mLogic.execute(action, args, listener, listenerExtras);
        				
        				Log.d(TAG, "logicResult = " + logicResult.toString());
        				
        				PluginResult pluginResult = transformResult(logicResult);
        				
        				Log.d(TAG, "pluginResult = " + pluginResult.toString());
        				Log.d(TAG, "pluginResult.getMessage() = " + pluginResult.getMessage());
        				if(pluginResult.getKeepCallback())
        					Log.d(TAG, "Keep Callback");
        				else
        					Log.d(TAG, "Don't keep Callback");
        				
        				callbackContext.sendPluginResult(pluginResult);
        			}
        		});
        		
        		result = true;
        	} else {
        		result = false;
        	}
        	
        } catch (Exception ex) {
        	Log.d(TAG, "Exception - " + ex.getMessage());
        }
        
        return result;
        
    // ***************************************************************************    
        
    }
    
    /*
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if(this.mLogic != null) {
        	this.mLogic.onDestroy();
        	this.mLogic = null;
        }
        
        
        // **************************************
        if (otbtworker != null) {
            otbtworker.stop();
        }
    }
	*/
    
    /*
	 ************************************************************************************************
	 * Public Methods 
	 ************************************************************************************************
	 */
    
	public void handleUpdate(ExecuteResult logicResult, Object[] listenerExtras) {
		Log.d(TAG, "Starting handleUpdate");
		sendUpdateToListener(logicResult, listenerExtras);
		Log.d(TAG, "Finished handleUpdate");
	}
	
	public void closeListener(ExecuteResult logicResult, Object[] listenerExtras) {
		Log.d(TAG, "Starting closeListener");
		sendUpdateToListener(logicResult, listenerExtras);
		Log.d(TAG, "Finished closeListener");
	}
	
	/*
	 ************************************************************************************************
	 * Private Methods 
	 ************************************************************************************************
	 */
	private void sendUpdateToListener(ExecuteResult logicResult, Object[] listenerExtras) {
		try {
			if (listenerExtras != null && listenerExtras.length > 0) {
				Log.d(TAG, "Sending update");
				CallbackContext callback = (CallbackContext)listenerExtras[0];
		
				callback.sendPluginResult(transformResult(logicResult));
				Log.d(TAG, "Sent update");
			}
		} catch (Exception ex) {
			Log.d(TAG, "Sending update failed", ex);
		}
	}
	
	private PluginResult transformResult(ExecuteResult logicResult) {
		PluginResult pluginResult = null;
		
		Log.d(TAG, "Start of transformResult");
		if (logicResult.getStatus() == ExecuteStatus.OK) {
			Log.d(TAG, "Status is OK");
			
			if (logicResult.getData() == null) {
				Log.d(TAG, "We dont have data");
				pluginResult = new PluginResult(PluginResult.Status.OK);
			} else {
				Log.d(TAG, "We have data");
				pluginResult = new PluginResult(PluginResult.Status.OK, logicResult.getData());
			}
		}

		if (logicResult.getStatus() == ExecuteStatus.ERROR) {
			Log.d(TAG, "Status is ERROR");
			
			if (logicResult.getData() == null) {
				Log.d(TAG, "We dont have data");
				pluginResult = new PluginResult(PluginResult.Status.ERROR, "Unknown error");
			} else {
				Log.d(TAG, "We have data");
				pluginResult = new PluginResult(PluginResult.Status.ERROR, logicResult.getData());
			}
		}
		
		if (logicResult.getStatus() == ExecuteStatus.INVALID_ACTION) {
			Log.d(TAG, "Status is INVALID_ACTION");
			
			if (logicResult.getData() == null) {
				Log.d(TAG, "We have data");
				pluginResult = new PluginResult(PluginResult.Status.INVALID_ACTION, "Unknown error");
			} else {
				Log.d(TAG, "We dont have data");
				pluginResult = new PluginResult(PluginResult.Status.INVALID_ACTION, logicResult.getData());
			}
		}
		
		if (!logicResult.isFinished()) {
			Log.d(TAG, "Keep Callback set to true");
			pluginResult.setKeepCallback(true);
		}
		
		Log.d(TAG, "End of transformResult");
		return pluginResult;
	}
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    // ********************************************************************
    
    
    
    /*
    private void listBondedDevices(CallbackContext callbackContext) throws JSONException {
        JSONArray deviceList = new JSONArray();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
        	if(device.getName().toLowerCase().contains("tron")){
	            JSONObject json = new JSONObject();
	            json.put("name", device.getName());
	            json.put("address", device.getAddress());
	            json.put("id", device.getAddress());
	            if (device.getBluetoothClass() != null) {
	                json.put("class", device.getBluetoothClass().getDeviceClass());
	            }
	            deviceList.put(json);
        	}
        }
        callbackContext.success(deviceList);
    }

    private void connect(CordovaArgs args, boolean secure, CallbackContext callbackContext) throws JSONException {
        String macAddress = args.getString(0);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);

        if (device != null) {
            connectCallback = callbackContext;
            otbtworker.connect(device, secure);

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else {
            callbackContext.error("Could not connect to " + macAddress);
        }
    }

    // The Handler that gets information back from the BluetoothSerialService
    // Original code used handler for the because it was talking to the UI.
    // Consider replacing with normal callbacks
    private final Handler mHandler = new Handler() {

         public void handleMessage(Message msg) {
             switch (msg.what) {
                 case MESSAGE_READ:
                    buffer.append((String)msg.obj);
                    
                    if (dataAvailableCallback != null) {
                        sendDataToSubscriber();
                    }
                    /*
                    if(jogDataCallback != null){ //nwags
                    	sendJogDataToSubscriber();
                    }
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

    private void notifyConnectionLost(String error) {
        if (connectCallback != null) {
            connectCallback.error(error);
            connectCallback = null;
        }
    }

    private void notifyConnectionSuccess() {
        if (connectCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            connectCallback.sendPluginResult(result);
        }
    }
/*
    private void sendDataToSubscriber() {
    	LOG.d(TAG, "sendDataToSubscriber called");
    	String data = readUntil("\n");
    	String jsonStr = "";
    	if(data != null && data.length() > 0){
    		try {
    			
    			JSONObject json = new JSONObject(data);
    			if (json.has("r")) {
    				jsonStr = processBody(json.getJSONObject("r"));
    			}else if (json.has("sr")) {
    				jsonStr = processStatusReport(json.getJSONObject("sr"));
    			}
    			PluginResult result = new PluginResult(PluginResult.Status.OK, jsonStr);
                result.setKeepCallback(true);
                dataAvailableCallback.sendPluginResult(result);

    		} catch(Exception e){
    			if(e.getMessage()!=null)
    				Log.e(TAG, e.getMessage());
    			
    		}
    		
    		
            sendDataToSubscriber();
    	}
    	
    	
    	
        String data = readUntil(delimiter);
        if (data != null && data.length() > 0) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            dataAvailableCallback.sendPluginResult(result);

            sendDataToSubscriber();
        }
    }

    private int available() {
        return buffer.length();
    }

    private String read() {
        int length = buffer.length();
        String data = buffer.substring(0, length);
        buffer.delete(0, length);
        return data;
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

    //nwags private methods
    
    private void sendJogDataToSubscriber() {
    	LOG.d(TAG, "sendJogDataToSubscriber called");
    	String data = readUntil("\n");
    	String jsonStr = "";
    	if(data != null && data.length() > 0){
    		try {
    			
    			JSONObject json = new JSONObject(data);
    			if (json.has("r")) {
    				jsonStr = processBody(json.getJSONObject("r"));
    			}else if (json.has("sr")) {
    				jsonStr = processStatusReport(json.getJSONObject("sr"));
    			}
    			
    		} catch(Exception e){
    			if(e.getMessage()!=null)
    				Log.e(TAG, e.getMessage());
    			
    		}
    		
    		PluginResult result = new PluginResult(PluginResult.Status.OK, jsonStr);
            result.setKeepCallback(true);
            jogDataCallback.sendPluginResult(result);

            sendJogDataToSubscriber();
    	}
    }
    
    private String processBody(JSONObject json) throws JSONException {
    	LOG.d(TAG, "processBody called");
    	String result = "";
    	if(json.has("sr"))
    		result = processStatusReport(json.getJSONObject("sr"));
    	return result;
    }
    
    private String processStatusReport(JSONObject sr) throws JSONException{
    	LOG.d(TAG, "processStatusReport called");
    	String result = "";
    	JSONObject jResult = new JSONObject();
    	if (sr.has("posx"))
    		posx = sr.getDouble("posx");
    		jResult.put("x", posx);
		if (sr.has("posy"))
			posy = sr.getDouble("posy");
			jResult.put("y", posy);
		if (sr.has("posz"))
			posz = sr.getDouble("posz");
			jResult.put("z", posz);
		if (sr.has("posa"))
			posa = sr.getDouble("posa");
			jResult.put("a", posa);
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
    	return result;
    }
    
    private void jog(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    	Log.d(TAG, "jog called");
    	
    	String whatevs = args.getString(0);
    	whatevs.trim();
    	JSONObject jsonObj = new JSONObject(whatevs);
    	Log.d(TAG, "jsonObj: "+jsonObj.toString());
    	if(jsonObj.has("reset")) {
    		LOG.d(TAG, "reset");
    		byte[] rst = {0x18};
    		otbtworker.write(rst);
            callbackContext.success();
            return;
    	}
    	if(jsonObj.has("stop")) {
    		LOG.d(TAG, "stop");
    		otbtworker.write("!%\n".getBytes());
            callbackContext.success();
            return;
    	}
    	if(jsonObj.has("power")) {
    		LOG.d(TAG, "power");
    		if(jsonObj.getBoolean("power")){
    			otbtworker.write("$me\n".getBytes());
    			otbtworker.write("{\"1pm\":\"1\"}\n".getBytes());
    			otbtworker.write("{\"2pm\":\"1\"}\n".getBytes());
    			otbtworker.write("{\"3pm\":\"1\"}\n".getBytes());
    			otbtworker.write("{\"4pm\":\"1\"}\n".getBytes());
    			power = true;
                callbackContext.success();
                
    		}else{
    			otbtworker.write("$md\n".getBytes());
    			power = false;
                callbackContext.success();
    		}
    	}
    	
    	String gogoStr = "";
    	String gocode = "{\"gc\":\"g90 g0 ";//\"}\n";
    	if(jsonObj.has("x")||jsonObj.has("X")) {
    		Log.d(TAG, "has x");
    		Log.d(TAG, "and x="+jsonObj.getDouble("x"));
    		double gox = jsonObj.getDouble("x");//*xmax;
    		gogoStr = "x"+String.valueOf(gox);
    		gocode+=gogoStr;
    	}
    	if(jsonObj.has("y")) {
    		Log.d(TAG, "has y");
    		Log.d(TAG, "and y="+jsonObj.getDouble("y"));
    		double goy = jsonObj.getDouble("y");//*ymax;
    		gogoStr = "y"+String.valueOf(goy);
    		gocode+=gogoStr;
    	}
    	if(jsonObj.has("z")) {
    		double goz = jsonObj.getDouble("z");//*zmax;
    		gogoStr = "z"+String.valueOf(goz);
    		gocode+=gogoStr;
    	}
    	if(jsonObj.has("a")) {
    		double goa = jsonObj.getDouble("a");//*amax;
    		gogoStr = "a"+String.valueOf(goa);
    		gocode+=gogoStr;
    	}
    	gocode+="\"}\n";
    	otbtworker.write(gocode.getBytes());
        callbackContext.success();
    }
    
    private void setDimensions(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    	JSONObject jsonObj = args.getJSONObject(0);
    	
    	if(jsonObj.has("xmax")) {
    		xmax = jsonObj.getDouble("xmax");
    	}
    	if(jsonObj.has("ymax")) {
    		ymax = jsonObj.getDouble("ymax");
    	}
    	if(jsonObj.has("zmax")) {
    		zmax = jsonObj.getDouble("zmax");
    	}
    	if(jsonObj.has("amax")) {
    		amax = jsonObj.getDouble("amax");
    	}
    	
    }
    
    private void getDimensions(CallbackContext callbackContext) throws JSONException{
    	JSONObject json = new JSONObject();
    	json.put("xmax", xmax);
    	json.put("ymax", ymax);
    	json.put("zmax", zmax);
    	json.put("amax", amax);
    	callbackContext.success(json);
    }

    private void home(CallbackContext callbackContext){
    	//TODO: home
    	otbtworker.write("{\"gc\":\"G28.2 X0 Y0 Z0\"}\n".getBytes());
    	
    	callbackContext.success();
    }
    */
}
