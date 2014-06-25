package com.opentrons.otbtalpha.cordova;   

interface OTBTListenerAlpha {     
	void handleUpdate(); 
	String getUniqueID();
	
	void sendMessage(String data);
	void notifySuccess();
	void notifyError(String error);
} 
