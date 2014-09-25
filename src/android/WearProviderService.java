package net.trentgardner.cordova.androidwear;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.List;

public class WearProviderService extends Service implements
        MessageApi.MessageListener,
        NodeApi.NodeListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static final String MESSAGE_RECEIVED_PATH = "net.trentgardner.cordova.androidwear.NewMessage";

    private final List<WearMessageListener> listeners = new ArrayList<WearMessageListener>();
    private final List<String> nodes = new ArrayList<String>();

    private static final String TAG = WearProviderService.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;
    private Thread mBackgroundThread;
    private Handler mBackgroundHandler;

    private final WearMessageApi.Stub apiEndpoint = new WearMessageApi.Stub() {
        @Override
        public void sendData(String nodeId, String data) throws RemoteException {
            LOGD(TAG, "WearMessageApi.sendData");
            WearProviderService.this.sendData(nodeId, data);
        }

        @Override
        public void addListener(WearMessageListener listener) throws RemoteException {
            LOGD(TAG, "WearMessageApi.addListener");

            synchronized (listeners) {
                listeners.add(listener);
            }

            LOGD(TAG, String.format("Notifying listener of %d connected nodes", nodes.size()));
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

            Looper.loop();
        }
    }

    @Override
    public void onCreate() {
        LOGD(TAG, "onCreate");

        super.onCreate();

        mBackgroundThread = new BackgroundThread();
        mBackgroundThread.start();

        // Build a new GoogleApiClient
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mGoogleApiClient.connect();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LOGD(TAG, "onDestroy");

        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        LOGD(TAG, "onBind");
        return apiEndpoint;
    }

    private void addNode(final String nodeId) {
        LOGD(TAG, "addNode");
        if (nodes.contains(nodeId)) return;

        nodes.add(nodeId);

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listeners) {
                    for(int i = 0; i < listeners.size(); ++i) {
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

        nodes.remove(nodes.indexOf(nodeId));

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listeners) {
                    for(int i = 0; i < listeners.size(); ++i) {
                        try {
                            listeners.get(i).onError(nodeId, "Connection dead");
                        } catch (RemoteException e) {
                            Log.w(TAG, "Failed to notify listener ", e);
                            listeners.remove(i--);
                        }
                    }
                }
            }
        });
    }

    private void dataReceived(final String nodeId, final String message) {
        LOGD(TAG, String.format("dataReceived - nodeId: %s", nodeId));

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listeners) {
                    LOGD(TAG, String.format("Notifying %d listeners", listeners.size()));

                    for(int i = 0; i < listeners.size(); ++i) {
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

    private void sendData(final String nodeId, final String data) {
        LOGD(TAG, "sendMessage");

        final byte[] message = data.getBytes();

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                MessageApi.SendMessageResult result =
                        Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId,
                                MESSAGE_RECEIVED_PATH, message).await();

                if (result.getStatus().isSuccess()) {
                    Log.v(TAG, "Message sent to : " + nodeId);
                } else {
                    // Log an error
                    Log.v(TAG, "MESSAGE ERROR: failed to send Message");
                }
            }
        });
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            LOGD(TAG, "Connected to Google Api Service");
        }

        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.NodeApi.addListener(mGoogleApiClient, this);

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes =
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    addNode(node.getId());
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        LOGD(TAG, "Connection to Google API client was suspended");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals(MESSAGE_RECEIVED_PATH)) {
            final String message = new String(messageEvent.getData());
            dataReceived(messageEvent.getSourceNodeId(), message);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        LOGD(TAG, "Connection to Google API client was suspended");

        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
    }

    @Override
    public void onPeerConnected(final Node node) {
        LOGD(TAG, "onPeerConnected");
        addNode(node.getId());
    }

    @Override
    public void onPeerDisconnected(final Node node) {
        LOGD(TAG, "onPeerDisconnected");
        removeNode(node.getId());
    }

    /**
     * As simple wrapper around Log.d
     */
    private static void LOGD(final String tag, String message) {
        //if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        //}
    }
}
