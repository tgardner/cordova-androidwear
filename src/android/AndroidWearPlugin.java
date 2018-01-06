package net.trentgardner.cordova.androidwear;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class AndroidWearPlugin extends CordovaPlugin {
	private final String TAG = AndroidWearPlugin.class.getSimpleName();

	private WearMessageApi api = null;
	private Intent serviceIntent = null;

	private Hashtable<String, WearConnection> connections = new Hashtable<>();

	private ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			api = WearMessageApi.Stub.asInterface(service);
			try {
				api.addListener(messageListener);
				Log.i(TAG, "Listener registered with service");
			} catch (RemoteException e) {
				Log.e(TAG, "Failed to add listener", e);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			api = null;
			Log.i(TAG, "Service connection closed!");
		}
	};

	private WearMessageListener.Stub messageListener = new WearMessageListener.Stub() {

		@Override
		public void onConnect(String connectionId) throws RemoteException {
			Log.d(TAG, "messageListener.onConnect");

			createNewWearConnection(connectionId);
		}

		@Override
		public void onDataReceived(String nodeId, String data)
				throws RemoteException {
			Log.d(TAG, String.format("messageListener.onDataReceived - nodeId: %s", nodeId));

			WearConnection connection = connections.get(nodeId);
			if (connection == null)
				connection = createNewWearConnection(nodeId);

			connection.onDataReceived(data);
		}

		@Override
		public void onError(String connectionId, String data)
				throws RemoteException {
			Log.d(TAG, "messageListener.onError");

			WearConnection connection = connections.get(connectionId);
			if (connection != null) {
				connection.onError(data);
				connections.remove(connectionId);
			}
		}

	};

	private WearConnection createNewWearConnection(String connectionId) {
		WearConnection connection = new WearConnection(connectionId);
		connections.put(connectionId, connection);

		notifyCallbacksOfConnection(connectionId);

		return connection;
	}

	private class WearConnection {
		private String mHandle;

		private List<CallbackContext> dataCallbacks = new ArrayList<>();
		private List<CallbackContext> errorCallbacks = new ArrayList<>();

		WearConnection(String handle) {
			mHandle = handle;
		}

		void addDataListener(CallbackContext callback) {
			dataCallbacks.add(callback);
		}

		void addErrorListener(CallbackContext callback) {
			errorCallbacks.add(callback);
		}

		void onDataReceived(String data) {
			notifyCallbacks(dataCallbacks, createJSONObject(mHandle, data));
		}

		void onError(String data) {
			notifyCallbacks(errorCallbacks, createJSONObject(mHandle, data));
		}

		private void notifyCallbacks(final List<CallbackContext> callbacks,
									 final JSONObject data) {
			cordova.getActivity().runOnUiThread(() -> {
				Log.d(TAG, String.format("Notifying %d callbacks", callbacks.size()));

				for (CallbackContext context : callbacks) {
					keepCallback(context, data);
				}
			});
		}
	}

	private List<CallbackContext> connectCallbacks = new ArrayList<>();

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		Log.d(TAG, "initialize");

		Activity context = cordova.getActivity();

		serviceIntent = new Intent(context, WearProviderService.class);

		Log.d(TAG, "Attempting to start service");
		context.startService(serviceIntent);

		Log.d(TAG, "Attempting to bind to service");
		context.bindService(serviceIntent, serviceConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public boolean execute(String action, CordovaArgs args,
						   CallbackContext callbackContext) throws JSONException {

		final String ACTION_ONCONNECT = "onConnect";
		final String ACTION_ONDATARECEIVED = "onDataReceived";
		final String ACTION_ONERROR = "onError";
		final String ACTION_SENDDATA = "sendData";

		if (ACTION_ONCONNECT.equals(action))
			onConnect(callbackContext);
		else if (ACTION_ONDATARECEIVED.equals(action))
			onDataReceived(args, callbackContext);
		else if (ACTION_ONERROR.equals(action))
			onError(args, callbackContext);
		else if (ACTION_SENDDATA.equals(action))
			sendData(args, callbackContext);
		else
			return false;

		return true;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");

		try {
			Activity context = cordova.getActivity();

			if (api != null)
				api.removeListener(messageListener);

			context.unbindService(serviceConnection);
			context.stopService(serviceIntent);
		} catch (Throwable t) {
			// catch any issues, typical for destroy routines
			// even if we failed to destroy something, we need to continue
			// destroying
			Log.w(TAG, "Failed to unbind from the service", t);
		}

		super.onDestroy();
	}

	private void sendData(final CordovaArgs args,
						  final CallbackContext callbackContext) throws JSONException {
		Log.d(TAG, "sendData");

		String connectionId = args.getString(0);
		String data = args.getString(1);
		try {
			if (api != null) {
				api.sendData(connectionId, data);
				callbackContext.success();
			} else {
				callbackContext.error("Service not present");
			}
		} catch (RemoteException e) {
			callbackContext.error(e.getMessage());
		}
	}

	private void onConnect(final CallbackContext callbackContext) throws JSONException {
		Log.d(TAG, "onConnect");

		connectCallbacks.add(callbackContext);

		// Alert the client of any existing connections
		Enumeration<String> keys = connections.keys();
		while (keys.hasMoreElements()) {
			connect(callbackContext, keys.nextElement());
		}
	}

	private void onDataReceived(final CordovaArgs args,
								final CallbackContext callbackContext) throws JSONException {
		Log.d(TAG, "onDataReceived");

		String connectionId = args.getString(0);
		WearConnection connection = connections.get(connectionId);
		if (connection != null) {
			connection.addDataListener(callbackContext);
		} else {
			callbackContext.error("Invalid connection handle");
		}
	}

	private void onError(final CordovaArgs args,
						 final CallbackContext callbackContext) throws JSONException {
		Log.d(TAG, "onError");

		String connectionId = args.getString(0);
		WearConnection connection = connections.get(connectionId);
		if (connection != null) {
			connection.addErrorListener(callbackContext);
		} else {
			callbackContext.error("Invalid connection handle");
		}
	}

	private void connect(CallbackContext callbackContext, String connectionId) {
		JSONObject o = createJSONObject(connectionId, null);
		keepCallback(callbackContext, o);
	}

	private void notifyCallbacksOfConnection(final String connectionId) {
		this.cordova.getActivity().runOnUiThread(() -> {
			for (CallbackContext context : connectCallbacks) {
				connect(context, connectionId);
			}
		});
	}

	private void keepCallback(final CallbackContext callbackContext,
							  JSONObject message) {
		PluginResult r = new PluginResult(PluginResult.Status.OK, message);
		r.setKeepCallback(true);
		callbackContext.sendPluginResult(r);
	}

	private JSONObject createJSONObject(String handle, String data) {
		JSONObject o = new JSONObject();

		try {
			o.put("handle", handle);

			if (data != null)
				o.put("data", data);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return o;
	}
}
