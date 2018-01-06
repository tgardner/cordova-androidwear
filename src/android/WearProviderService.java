package net.trentgardner.cordova.androidwear;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WearProviderService extends Service implements
		MessageClient.OnMessageReceivedListener,
		CapabilityClient.OnCapabilityChangedListener {

	private static final String MESSAGE_PATH = "/NewMessage";
	private static final String CORDOVA_CAPABILITY = "cordova_messaging";

	private final List<WearMessageListener> listeners = new ArrayList<WearMessageListener>();
	private final Set<String> nodes = new HashSet<String>();

	private static final String TAG = WearProviderService.class.getSimpleName();

	private Handler mBackgroundHandler;

	private final WearMessageApi.Stub apiEndpoint = new WearMessageApi.Stub() {
		@Override
		public void sendData(String nodeId, String data) throws RemoteException {
			LOGD(TAG, "WearMessageApi.sendMessage");
			WearProviderService.this.sendMessage(nodeId, data);
		}

		@Override
		public void addListener(WearMessageListener listener) throws RemoteException {
			LOGD(TAG, "WearMessageApi.addListener");

			synchronized (listeners) {
				listeners.add(listener);
			}

			LOGD(TAG, String.format(Locale.ENGLISH, "Notifying listener of %d connected nodes", nodes.size()));
			for (String node : nodes) {
				listener.onConnect(node);
			}
		}

		@Override
		public void removeListener(WearMessageListener listener) throws RemoteException {
			LOGD(TAG, "WearMessageApi.removeListener");

			synchronized (listeners) {
				listeners.remove(listener);
			}
		}
	};


	private final class BackgroundThread extends Thread {
		@Override
		public void run() {
			Looper.prepare();

			mBackgroundHandler = new Handler();

			// Load initial nodes
			loadNodes();

			Looper.loop();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

		LOGD(TAG, "onCreate");
		Wearable.getMessageClient(this).addListener(this);
		Wearable.getCapabilityClient(this)
				.addListener(
						this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE);

		Thread mBackgroundThread = new BackgroundThread();
		mBackgroundThread.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		LOGD(TAG, "onDestroy");

		Wearable.getMessageClient(this).removeListener(this);
		Wearable.getCapabilityClient(this).removeListener(this);

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		LOGD(TAG, "onBind: " + intent);
		return apiEndpoint;
	}

	@Override
	public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {
		LOGD(TAG, "onCapabilityChanged: " + capabilityInfo);

		syncNodes(capabilityInfo.getNodes());
	}

	@Override
	public void onMessageReceived(@NonNull MessageEvent messageEvent) {
		LOGD(TAG, "onMessageReceived");
		if (messageEvent.getPath().equals(MESSAGE_PATH)) {
			final String message = new String(messageEvent.getData());
			messageReceived(messageEvent.getSourceNodeId(), message);
		}
	}

	private void loadNodes() {
		Task<CapabilityInfo> capabilityTask = Wearable.getCapabilityClient(this)
				.getCapability(CORDOVA_CAPABILITY, CapabilityClient.FILTER_REACHABLE);
		capabilityTask.addOnSuccessListener(new OnSuccessListener<CapabilityInfo>() {
			@Override
			public void onSuccess(CapabilityInfo capabilityInfo) {
				WearProviderService.this.onCapabilityChanged(capabilityInfo);
			}
		});
	}

	private void syncNodes(final Set<Node> nodes) {
		LOGD(TAG, "syncNodes : " + nodes.size());

		List<String> nodeIds = new ArrayList<String>();

		for (Node node : nodes) {
			nodeIds.add(node.getId());
		}

		for (String nodeId : this.nodes) {
			if (nodeIds.contains(nodeId)) continue;

			removeNode(nodeId);
		}

		for (String nodeId : nodeIds) {
			if (this.nodes.contains(nodeId)) continue;

			addNode(nodeId);
		}
	}

	private void addNode(final String nodeId) {
		LOGD(TAG, "addNode");
		if (nodes.contains(nodeId)) return;

		nodes.add(nodeId);

		mBackgroundHandler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (int i = 0; i < listeners.size(); ++i) {
						try {
							listeners.get(i).onConnect(nodeId);
						} catch (RemoteException e) {
							Log.w(TAG, "Failed to notify listener ", e);
							listeners.remove(i--);
						}
					}
				}
			}
		});
	}

	private void removeNode(final String nodeId) {
		LOGD(TAG, "removeNode");
		if (!nodes.contains(nodeId)) return;

		nodes.remove(nodeId);

		mBackgroundHandler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (int i = 0; i < listeners.size(); ++i) {
						try {
							listeners.get(i).onError(nodeId,
									String.format(Locale.ENGLISH, "Node disconnected: %s", nodeId));
						} catch (RemoteException e) {
							Log.w(TAG, "Failed to notify listener ", e);
							listeners.remove(i--);
						}
					}
				}
			}
		});
	}

	private void messageReceived(final String nodeId, final String message) {
		LOGD(TAG, String.format("messageReceived - nodeId: %s", nodeId));

		mBackgroundHandler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					LOGD(TAG, String.format(Locale.ENGLISH, "Notifying %d listeners", listeners.size()));

					for (int i = 0; i < listeners.size(); ++i) {
						try {
							listeners.get(i).onDataReceived(nodeId, message);
						} catch (RemoteException e) {
							Log.w(TAG, "Failed to notify listener ", e);
							listeners.remove(i--);
						}
					}
				}
			}
		});
	}

	private void sendMessage(final String nodeId, final String data) {
		LOGD(TAG, "sendMessage");

		final byte[] message = data.getBytes();

		mBackgroundHandler.post(new Runnable() {
			@Override
			public void run() {
				Task<Integer> result = Wearable.getMessageClient(WearProviderService.this)
						.sendMessage(nodeId, MESSAGE_PATH, message);
				result.addOnSuccessListener(new OnSuccessListener<Integer>() {
					@Override
					public void onSuccess(Integer aVoid) {
						LOGD(TAG, "Message sent to : " + nodeId);
					}
				});
				result.addOnFailureListener(new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception aVoid) {
						LOGD(TAG, "Message send failed");
					}
				});
			}
		});
	}

	/**
	 * As simple wrapper around Log.d
	 */
	private static void LOGD(final String tag, final String message) {
		//if (Log.isLoggable(tag, Log.DEBUG)) {
		Log.d(tag, message);
		//}
	}
}
