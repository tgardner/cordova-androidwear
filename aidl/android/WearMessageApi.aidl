package net.trentgardner.cordova.androidwear;

import net.trentgardner.cordova.androidwear.WearMessageListener;

interface WearMessageApi {
	void sendData(String connectionId, String data);
   
	void addListener(WearMessageListener listener);
	
	void removeListener(WearMessageListener listener);
}
