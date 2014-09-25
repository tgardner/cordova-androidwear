package net.trentgardner.cordova.androidwear;

interface WearMessageListener {
	void onConnect(String connectionId);
	
	void onDataReceived(String connectionId, String data);
	
	void onError(String connectionId, String data);
}
