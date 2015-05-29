package electria.electriahrm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.graphics.Point;

import org.achartengine.GraphicalView;


public class MainActivity extends Activity {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "ElectriaHRM";
    private static final String DIRECTORY_NAME = "/ECGDATA";
    private static final int CONNECTED = 20;
    private static final int DISCONNECTED = 21;
    private static final int CONNECTING = 22;
    private static final int X_RANGE = 500;
    private static final int DEFAULT_BATTERY_LEVEL = 0;
    private static final long MAX_DATA_RECORDING_TIME = 120;//One hour
    private static final int AVERAGE_COLLECTION_SIZE = 5000;
    private static final int DATA_SAVING_INTERVAL = 60000;//

    private boolean showGraph;
    private boolean graphViewActive;
    private boolean dataRecording;

    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private TextView batLevelView;
    private EditText edtMessage;
    private LinearLayout.LayoutParams param_enable, param_disable;
    private Button btnConnectDisconnect,btnShow,btnSend,btnStore, btnHistory;
    private ViewGroup mainLayout;
    private List<String> collection;

    private int mCounter;
    private int lastBatLevel;
    private int recordTimerCounter, min, sec, hour;
    private BleService mService;
    private int mState;
    private String fileName;
    private String timerString;
    private Handler mHandler;
    private BluetoothDevice mDevice;
    private BluetoothAdapter mBtAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnConnectDisconnect=(Button) findViewById(R.id.btn_connect);
        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.green));
        btnShow=(Button) findViewById(R.id.btn_show);
        btnShow.setBackgroundColor(getResources().getColor(R.color.blue));
        btnSend=(Button) findViewById(R.id.sendButton);
        btnStore = (Button)findViewById(R.id.btn_store);
        btnStore.setBackgroundColor(getResources().getColor(R.color.green));
        btnHistory = (Button)findViewById(R.id.btn_history);
        btnHistory.setBackgroundColor(getResources().getColor(R.color.blue));
        edtMessage=(EditText) findViewById(R.id.sendText);
        batLevelView = (TextView) findViewById(R.id.bat_level);
        param_enable = new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT, 2.0f);
        param_disable = new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT, 0.0f);
        collection = new ArrayList<String>();

        dataRecording = false;
        showGraph = false;
        graphViewActive = false;

        collection = new ArrayList<String>();
        mCounter = lastBatLevel = 0;
        recordTimerCounter = min = sec =  hour = 0;
        fileName = null;
        mService = null;
        mDevice = null;
        timerString = "";
        mState = DISCONNECTED;
        mHandler = new Handler();

        service_init();

        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                    if (mState == DISCONNECTED){
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice != null) {
                            mService.disconnect();
                        }
                    }
                }
            }
        });

        edtMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSend.setEnabled(true);
                edtMessage.setHint("");
            }
        });

        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = edtMessage.getText().toString();
                byte[] value;
                try {
                    //send data to service
                    value = message.getBytes("UTF-8");
                    if(value != null)
                        mService.writeTXCharacteristic(value);
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, e.getMessage());
                }
                edtMessage.setText("");
                btnSend.setEnabled(false);
                edtMessage.setHint(R.string.text_hint);
                //Close keyboard
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edtMessage.getWindowToken(), 0);
            }
        });

        // Handle Plot Graph button
        btnShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == CONNECTED) {
                    if (showGraph) {
                        showGraph = false;
                        btnShow.setBackgroundColor(getResources().getColor(R.color.blue));
                        btnShow.setText("Show");
                    }else{
                        showGraph = true;
                        btnShow.setBackgroundColor(getResources().getColor(R.color.yellow));
                        btnShow.setText("Pause");
                    }
                }
            }
        });

        // Handle Record button
        btnStore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == CONNECTED) {
                    if(dataRecording){
                        stopRecordingData();
                    }
                    else{
                        dataRecording = true;
                        btnStore.setText("Stop");
                        startRecordingData();
                    }
                }
            }
        });

        // Handle Record button
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == DISCONNECTED) {
                    Intent intent = new Intent(MainActivity.this, History.class);
                    intent.putExtra(Intent.EXTRA_TEXT, DIRECTORY_NAME);
                    startActivity(intent);
                }
            }
        });
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = LineGraphView.getLineGraphView();
        mGraphView = mLineGraph.getView(this);
        mainLayout = (ViewGroup) findViewById(R.id.graph_layout);
        mainLayout.addView(mGraphView);
        graphViewActive = true;
    }

    //Plot a new set of values on the graph and present on the GUI
    private void updateGraph(int value) {
        final int ecg = value;
        if(value <= 700 && value >=200) {
            double maxX = mCounter;
            double minX = (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
            mLineGraph.setRange(minX, maxX, 200, 700);
            mLineGraph.addValue(new Point(mCounter, ecg));
            mGraphView.repaint();
            mCounter += 2;
        }
    }

    private void updateBatteryLevel(int level) {
        if(lastBatLevel != level)
            lastBatLevel = level;
        batLevelView.setText("Battery Level: " + lastBatLevel + "%");
    }

    private void clearGraph() {
        if(graphViewActive) {
            graphViewActive = false;
            showGraph = false;
            mLineGraph.clearGraph();
            mCounter = 0;
            mainLayout.removeView(mGraphView);
        }
    }

    private void resetGUIComponents(){
        btnShow.setBackgroundColor(getResources().getColor(R.color.blue));
        btnShow.setText("Show");
        btnStore.setText("Record");
        btnConnectDisconnect.setText("Connect");
        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.green));
        edtMessage.setHint("");
        edtMessage.setEnabled(false);
        btnShow.setLayoutParams(param_disable);
        btnStore.setLayoutParams(param_disable);
        btnHistory.setLayoutParams(param_enable);
        batLevelView.setText("");
        ((TextView) findViewById(R.id.deviceName)).setText(R.string.no_device);
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((BleService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BleService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "UART_CONNECT_MSG");
                        btnConnectDisconnect.setText("Disconnect");
                        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.red));
                        edtMessage.setHint(R.string.text_hint);
                        edtMessage.setEnabled(true);
                        btnShow.setLayoutParams(param_enable);
                        btnStore.setLayoutParams(param_enable);
                        btnHistory.setLayoutParams(param_disable);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ "- Connected");
                        setGraphView();
                        mState = CONNECTED;
                    }
                });
            }

            if (action.equals(BleService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "DISCONNECT_MSG");
                        mService.close();
                        clearGraph();
                        resetGUIComponents();
                        if(dataRecording)
                            stopRecordingData();
                        mState = DISCONNECTED;
                    }
                });
            }

            if (action.equals(BleService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableRXNotification();
            }

            if (action.equals(BleService.ACTION_RX_DATA_AVAILABLE)) {
                String rxString = intent.getStringExtra(BleService.EXTRA_DATA);
                if (rxString != null){
                    String [] ECGData = rxString.split("-");
                    Log.d(TAG, "Packet Recieved: " +ECGData[0] + "---" + ECGData[1]);
                    if(dataRecording) {
                        collection.add(ECGData[0]);
                        collection.add(ECGData[1]);
                    }
                    if(showGraph) {
                        updateGraph(Integer.parseInt(ECGData[0]));
                        updateGraph(Integer.parseInt(ECGData[1]));
                    }
                }
            }

            if (action.equals(BleService.ACTION_BATTERY_LEVEL_DATA_AVAILABLE)){
                final int batValue = intent.getIntExtra(BleService.EXTRA_DATA, DEFAULT_BATTERY_LEVEL);
                runOnUiThread(new Runnable() {
                    public void run() {
                        updateBatteryLevel(batValue);
                    }
                });
            }
            if (action.equals(BleService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }
            if(action.equals(BleService.ACTION_TX_CHAR_WRITE)){
                Log.d(TAG, "Write RX done");
            }

        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BleService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private void startRecordingData(){
        mSaveDataTask.run();
        mRecordTimer.run();
    }

    private void stopRecordingData(){
        if(dataRecording) {
            saveToDisk(fileName);
            dataRecording = false;
            btnStore.setText("Record");
            mHandler.removeCallbacks(mSaveDataTask);
            mHandler.removeCallbacks(mRecordTimer);
            ((TextView) findViewById(R.id.timer_view)).setText("");
            fileName = null;
            refreshTimer();
        }
    }

    private Runnable mSaveDataTask = new Runnable() {
        @Override
        public void run() {
            if(collection.size() >= AVERAGE_COLLECTION_SIZE)
                saveToDisk(fileName);
            if (dataRecording)
                mHandler.postDelayed(mSaveDataTask, DATA_SAVING_INTERVAL);
        }
    };

    private void saveToDisk(String fName){
        if(isExternalStorageWritable()){
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File (root.getAbsolutePath() + DIRECTORY_NAME);
            if(!dir.isDirectory())
                dir.mkdirs();
            File file;
            if(fName == null) {//Get a unique fileName
                do {
                    fName = getFileName();
                    file = new File(dir, fName);
                } while (file.exists());
                fileName = fName;
            }
            else{
                file = new File(dir, fName);
            }
            try {
                FileWriter fw = new FileWriter(file, true);
                String str = Arrays.toString(collection.toArray(new String[collection.size()]));
                str = str.substring(1, str.length()-1).replaceAll("\\s+","").replaceAll(",", "\n");
                fw.append(str+"\n");
                fw.flush();
                fw.close();
                collection.clear();
                showMessage("Saved ECG data to SD card");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.i(TAG, "******* File not found. Did you" +
                        " add a WRITE_EXTERNAL_STORAGE permission to the   manifest?");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else
            Log.w(TAG, "External storage not writable");
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private String getFileName(){
        String fN;
        Random rand = new Random(System.currentTimeMillis());
        fN = "ECG_"+rand.nextInt(1000);
        return fN;
    }

    private Runnable mRecordTimer = new Runnable() {
        @Override
        public void run() {
            if(recordTimerCounter < 60){
                sec = recordTimerCounter;
            }
            else if(recordTimerCounter < 360){
                min = recordTimerCounter/60;
                sec = recordTimerCounter%60;
            }
            else{
                hour = recordTimerCounter/360;
                min = (recordTimerCounter%360)/60;
                min = (recordTimerCounter%360)%60;
            }
            updateTimer();
            if(recordTimerCounter == MAX_DATA_RECORDING_TIME) {
                stopRecordingData();
                return;
            }
            recordTimerCounter++;
            mHandler.postDelayed(mRecordTimer, 1000);
        }
    };

    private void refreshTimer(){
        recordTimerCounter = hour = min = sec = 0;
    }

    private void updateTimer(){
        timerString = timerString.format("%02d:%02d:%02d", hour,min,sec);
        ((TextView) findViewById(R.id.timer_view)).setText(timerString);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_RX_DATA_AVAILABLE);
        intentFilter.addAction(BleService.ACTION_BATTERY_LEVEL_DATA_AVAILABLE);
        intentFilter.addAction(BleService.DEVICE_DOES_NOT_SUPPORT_UART);
        intentFilter.addAction(BleService.ACTION_TX_CHAR_WRITE);
        return intentFilter;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
        stopRecordingData();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if(mState == CONNECTED){
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.disconnect_message)
                    .setPositiveButton(R.string.popup_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        }else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.quit_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
