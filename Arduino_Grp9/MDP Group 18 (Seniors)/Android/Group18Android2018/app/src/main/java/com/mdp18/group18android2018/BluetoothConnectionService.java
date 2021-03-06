package com.mdp18.group18android2018;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import java.io.IOException;
import java.util.UUID;


public class BluetoothConnectionService extends IntentService {

    private static final String TAG = "BTConnectionAService";
    private static final String appName = "Group 18 Remote Controller";

    // UUID
    private static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Declarations
    private BluetoothAdapter myBluetoothAdapter;
    private AcceptThread myAcceptThread;
    private ConnectThread myConnectThread;
    public  BluetoothDevice myDevice;
    private UUID deviceUUID;
    Context myContext;


    // Constructor
    public BluetoothConnectionService() {

        super("BluetoothConnectionService");

    }


    // Handle Intent for Service
    // Starts When Service is Created
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        myContext = getApplicationContext();
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (intent.getStringExtra("serviceType").equals("listen")) {
            myDevice = (BluetoothDevice) intent.getExtras().getParcelable("device");
            Log.d(TAG, "Service Handle: startAcceptThread");
            startAcceptThread();
        } else {
            myDevice = (BluetoothDevice) intent.getExtras().getParcelable("device");
            deviceUUID = (UUID) intent.getSerializableExtra("id");
            Log.d(TAG, "Service Handle: startClientThread");
            startClientThread(myDevice, deviceUUID);
        }

    }

    // Listening for incoming connections until a connection is accepted or cancelled
    private class AcceptThread extends Thread {

        // Local server socket
        private final BluetoothServerSocket myServerSocket;


        public AcceptThread() {
            BluetoothServerSocket temp = null;

            // Create a new listening server socket
            try {
                temp = myBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, myUUID);
                Log.d(TAG, "AcceptThread: Setting up server using: " + myUUID);

            } catch (IOException e) {
                e.printStackTrace();
            }

            myServerSocket = temp;
        }

        public void run() {

            Log.d(TAG, "AcceptThread: Running");

            BluetoothSocket socket;
            Intent connectionStatusIntent;

            try {

                Log.d(TAG, "Run: RFCOM server socket start....");

                // Blocking call which will only return on a successful connection / exception
                socket = myServerSocket.accept();

                // Broadcast connection message
                connectionStatusIntent = new Intent("btConnectionStatus");
                connectionStatusIntent.putExtra("ConnectionStatus", "connect");
                connectionStatusIntent.putExtra("Device", BluetoothConnect.getBluetoothDevice());
                LocalBroadcastManager.getInstance(myContext).sendBroadcast(connectionStatusIntent);

                // Successfully connected
                Log.d(TAG, "Run: RFCOM server socket accepted connection");

                // Start BluetoothChat
                BluetoothChat.connected(socket, myDevice, myContext);


            } catch (IOException e) {

                connectionStatusIntent = new Intent("btConnectionStatus");
                connectionStatusIntent.putExtra("ConnectionStatus", "connectionFail");
                connectionStatusIntent.putExtra("Device",  BluetoothConnect.getBluetoothDevice());

                Log.d(TAG, "AcceptThread: Connection Failed ,IOException: " + e.getMessage());
            }

            Log.d(TAG, "Ended AcceptThread");

        }

        public void cancel() {

            Log.d(TAG, "Cancel: Canceling AcceptThread");

            try {
                myServerSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "Cancel: Closing AcceptThread Failed. " + e.getMessage());
            }
        }


    }

    // Attempt to make outgoing connection with device
    private class ConnectThread extends Thread {

        private BluetoothSocket mySocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {

            Log.d(TAG, "ConnectThread: started");
            myDevice = device;
            deviceUUID = uuid;
        }

        public void run() {
            BluetoothSocket temp = null;
            Intent connectionStatusIntent;

            Log.d(TAG, "Run: myConnectThread");

            // BluetoothSocket for connection with given BluetoothDevice
            try {
                Log.d(TAG, "ConnectThread: Trying to create InsecureRFcommSocket using UUID: " + myUUID);
                temp = myDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {

                Log.d(TAG, "ConnectThread: Could not create InsecureRFcommSocket " + e.getMessage());
            }

            mySocket = temp;

            // Cancel discovery to prevent slow connection
            myBluetoothAdapter.cancelDiscovery();

            try {

                Log.d(TAG, "Connecting to Device: " + myDevice);
                // Blocking call and will only return on a successful connection / exception
                mySocket.connect();


                // Broadcast connection message
                connectionStatusIntent = new Intent("btConnectionStatus");
                connectionStatusIntent.putExtra("ConnectionStatus", "connect");
                connectionStatusIntent.putExtra("Device", myDevice);
                LocalBroadcastManager.getInstance(myContext).sendBroadcast(connectionStatusIntent);

                Log.d(TAG, "run: ConnectThread connected");

                // Start BluetoothChat
                BluetoothChat.connected(mySocket, myDevice, myContext);

                // Cancel myAcceptThread for listening
                if (myAcceptThread != null) {
                    myAcceptThread.cancel();
                    myAcceptThread = null;
                }

            } catch (IOException e) {

                // Close socket on error
                try {
                    mySocket.close();

                    connectionStatusIntent = new Intent("btConnectionStatus");
                    connectionStatusIntent.putExtra("ConnectionStatus", "connectionFail");
                    connectionStatusIntent.putExtra("Device", myDevice);
                    LocalBroadcastManager.getInstance(myContext).sendBroadcast(connectionStatusIntent);
                    Log.d(TAG, "run: Socket Closed: Connection Failed!! " + e.getMessage());

                } catch (IOException e1) {
                    Log.d(TAG, "myConnectThread, run: Unable to close socket connection: " + e1.getMessage());
                }

            }

            try {

            } catch (NullPointerException e) {
                e.printStackTrace();
            }

        }

        public void cancel() {

            try {
                Log.d(TAG, "Cancel: Closing Client Socket");
                mySocket.close();
            } catch (IOException e) {
                Log.d(TAG, "Cancel: Closing mySocket in ConnectThread Failed " + e.getMessage());
            }
        }
    }

    // Start AcceptThread and Listen for Incoming Connection
    public synchronized void startAcceptThread() {

        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (myConnectThread != null) {
            myConnectThread.cancel();
            myConnectThread = null;
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (myAcceptThread == null) {
            myAcceptThread = new AcceptThread();
            myAcceptThread.start();
        }
    }

    // Start ConnectThread and attempt to make a connection
    public void startClientThread(BluetoothDevice device, UUID uuid) {

        Log.d(TAG, "startClient: Started");
        myConnectThread = new ConnectThread(device, uuid);
        myConnectThread.start();
    }
}