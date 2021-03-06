package electria.electriahrm.activities;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import electria.electriahrm.dataPackets.DataPacket;
import electria.electriahrm.fragments.Channel1Fragment;
import electria.electriahrm.fragments.Channel2Fragment;
import electria.electriahrm.fragments.Channel3Fragment;
import electria.electriahrm.measurements.ECGMeasurement;
import electria.electriahrm.R;
import electria.electriahrm.services.BleService;


public class MainActivity extends Activity {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "ElectriaHRM";
    private static final String DIRECTORY_NAME = "/ECGDATA";
    private static final int CONNECTED = 20;
    private static final int DISCONNECTED = 21;
    private static final int MAX_DATA_RECORDING_TIME = 120;//Two minutes(60 seconds)
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND = 1000;// 1000 milliseconds in one second

    private boolean mShowGraph;
    private boolean mDataRecording;

    private TextView sensorPosView, hrView, avHRView;
    private EditText editMessage;
    private LinearLayout.LayoutParams mParamEnable, mParamDisable;
    private Button btnConnectDisconnect,btnShow,btnSend,btnStore, btnHistory;
    private List<DataPacket> mRecordedData;
    private int mRecTimerCounter, min, sec, hr;
    private BleService mService;
    private int mState;
    private int mDataType;
    private double mAvHeartRate, mHeartRateCount;
    private String mTimerString;
    private String mSensorPos;
    private Handler mHandler;
    private BluetoothDevice mDevice;
    private ECGMeasurement ecgM;
    private BluetoothAdapter mBtAdapter = null;

    private Channel1Fragment mChannelOne;
    private Channel2Fragment mChannelTwo;
    private Channel3Fragment mChannelThree;

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
        editMessage=(EditText) findViewById(R.id.sendText);
        sensorPosView = (TextView) findViewById(R.id.sensor_position);
        hrView = (TextView) findViewById(R.id.heart_rate);
        avHRView = (TextView) findViewById(R.id.av_heart_rate);
        mParamEnable = new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT, 2.0f);
        mParamDisable = new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT, 0.0f);
        mRecordedData = new ArrayList<>();

        mChannelOne = new Channel1Fragment();
        mChannelTwo = new Channel2Fragment();
        mChannelThree = new Channel3Fragment();

        mDataRecording = false;
        mShowGraph = false;
        mRecTimerCounter = 1;
        mDataType = -1;
        min = sec =  hr = 0;
        mAvHeartRate = 0;
        mHeartRateCount = 0;
        mService = null;
        mDevice = null;
        mTimerString = "";
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

        editMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSend.setEnabled(true);
                editMessage.setHint("");
            }
        });

        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = editMessage.getText().toString();
                byte[] value;
                try {
                    //send data to service
                    value = message.getBytes("UTF-8");
                    if(value != null)
                        mService.writeTXCharacteristic(value);
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, e.getMessage());
                }
                editMessage.setText("");
                btnSend.setEnabled(false);
                editMessage.setHint(R.string.text_hint);
                //Close keyboard
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
            }
        });

        // Handle Show Graph button
        btnShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSensorPosition(mSensorPos);
                if (mState == CONNECTED) {
                    if (mShowGraph) {
                        stopGraph();
                    }else{
                        startGraph();
                    }
                }
            }
        });

        // Handle Record button
        btnStore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == CONNECTED) {
                    if(mDataRecording){
                        stopRecordingData();
                    }
                    else{
                        ecgM = new ECGMeasurement(mDevice.getName(),
                                new SimpleDateFormat("yyMMddHHmmss", Locale.US).format(new Date()));
                        mDataRecording = true;
                        btnStore.setText("Stop");
                        mRecordTimer.run();
                    }
                }
            }
        });

        // Handle History button
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

    private void startGraph(){
        if(setGraphLayout()) {
            mShowGraph = true;
            btnShow.setText("Close");
        }
    }

    private void stopGraph(){
        mShowGraph = false;
        clearGraphLayout();
        btnShow.setText("View");
        resetDisplayedSensorValues();
    }

    private void setSensorPosition(final String position) {
        if (position != null) {
            sensorPosView.setText("Position " + position.toUpperCase());
        } else {
            sensorPosView.setText(" ");
        }
    }

    private void setHeartRateValue(int value) {
        if (value != 0) {
            hrView.setText("HR  " + value + "BPM");
            mAvHeartRate = ((mAvHeartRate*mHeartRateCount)+value)/(++mHeartRateCount);
            avHRView.setText("AvHR " +Math.round(mAvHeartRate)+ "BPM");
        } else {
            hrView.setText(" ");
            avHRView.setText(" ");
        }
    }

    private void resetDisplayedSensorValues() {
        setSensorPosition(null);
        setHeartRateValue(0);
        mAvHeartRate = 0;
        mHeartRateCount = 0;
    }

    private void resetGUIComponents(){
        btnShow.setBackgroundColor(getResources().getColor(R.color.blue));
        btnShow.setText("View");
        btnStore.setText("Record");
        btnConnectDisconnect.setText("Connect");
        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.green));
        editMessage.setHint("");
        editMessage.setEnabled(false);
        btnShow.setLayoutParams(mParamDisable);
        btnStore.setLayoutParams(mParamDisable);
        btnHistory.setLayoutParams(mParamEnable);
        ((TextView) findViewById(R.id.deviceName)).setText(R.string.no_device);
    }

    //BLE service connected/disconnected
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
            mService = null;
        }
    };

    private final BroadcastReceiver BLEStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BleService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "CONNECT_MSG");
                        btnConnectDisconnect.setText("Disconnect");
                        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.red));
                        editMessage.setHint(R.string.text_hint);
                        editMessage.setEnabled(true);
                        btnShow.setLayoutParams(mParamEnable);
                        btnStore.setLayoutParams(mParamEnable);
                        btnHistory.setLayoutParams(mParamDisable);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName() + "- Connected");
                        mState = CONNECTED;
                    }
                });
            }

            if (action.equals(BleService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "DISCONNECT_MSG");
                        mService.close();
                        if(mShowGraph)
                            stopGraph();
                        resetGUIComponents();
                        if (mDataRecording)
                            stopRecordingData();
                        mState = DISCONNECTED;
                    }
                });
            }

            if (action.equals(BleService.THREE_CHANNEL_ECG)) {
                final int[] samples = intent.getIntArrayExtra(BleService.EXTRA_DATA) ;
                (new Runnable(){
                    public void run(){
                        if (samples != null ){
                            if(mDataType < 0)
                                mDataType = samples[0];
                            else {
                                if (mDataRecording)
                                    mRecordedData.add((new DataPacket(samples)));
                                if (mShowGraph) {
                                    mChannelOne.updateGraph(samples[2]);
                                    mChannelOne.updateGraph(samples[3]);
                                    mChannelTwo.updateGraph(samples[4]);
                                    mChannelTwo.updateGraph(samples[5]);
                                    mChannelThree.updateGraph(samples[6]);
                                    mChannelThree.updateGraph(samples[7]);
                                    Log.w(TAG, "Data Type: " + samples[0] + "Packet Number: " + samples[1] +
                                            " {Samples: " + samples[2] + "-" + samples[3] + "-" + samples[4] +
                                            "-" + samples[5] + "-" + samples[6] + "-" + samples[7] + "}");
                                }
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BleService.ONE_CHANNEL_ECG)) {
                final int[] samples = intent.getIntArrayExtra(BleService.EXTRA_DATA) ;
                (new Runnable(){
                    public void run(){
                        if (samples != null ){
                            if(mDataType < 0)
                                mDataType = samples[0];
                            else {
                                if (mDataRecording)
                                    mRecordedData.add((new DataPacket(samples)));
                                if (mShowGraph) {
                                    mChannelOne.updateGraph(samples[2]);
                                    mChannelOne.updateGraph(samples[3]);
                                    Log.w(TAG, "Data Type: " + samples[0] + "Packet Number: " + samples[1] +
                                            " {Samples: " + samples[2] + "-" + samples[3] + "-" + samples[4] +
                                            "-" + samples[5] + "-" + samples[6] + "-" + samples[7] + "}");
                                }
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BleService.TWO_CHANNEL_PPG)) {
                final int[] samples = intent.getIntArrayExtra(BleService.EXTRA_DATA) ;
                (new Runnable(){
                    public void run(){
                        if (samples != null ){
                            if(mDataType < 0)
                                mDataType = samples[0];
                            else {
                                if (mDataRecording)
                                    mRecordedData.add((new DataPacket(samples)));
                                if (mShowGraph) {
                                    mChannelOne.updateGraph(samples[2]);
                                    mChannelOne.updateGraph(samples[3]);
                                    mChannelTwo.updateGraph(samples[4]);
                                    mChannelTwo.updateGraph(samples[5]);
                                    Log.w(TAG, "Data Type: " + samples[0] + "Packet Number: " + samples[1] +
                                            " {Samples: " + samples[2] + "-" + samples[3] + "-" + samples[4] +
                                            "-" + samples[5] + "-" + samples[6] + "-" + samples[7] + "}");
                                }
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BleService.THREE_CHANNEL_ACCELERATION)) {
                final int[] samples = intent.getIntArrayExtra(BleService.EXTRA_DATA) ;
                (new Runnable(){
                    public void run(){
                        if (samples != null ){
                            if(mDataType < 0)
                                mDataType = samples[0];
                            else {
                                if (mDataRecording)
                                    mRecordedData.add((new DataPacket(samples)));
                                if (mShowGraph) {
                                    mChannelOne.updateGraph(samples[2]);
                                    mChannelOne.updateGraph(samples[3]);
                                    mChannelTwo.updateGraph(samples[4]);
                                    mChannelTwo.updateGraph(samples[5]);
                                    mChannelThree.updateGraph(samples[6]);
                                    mChannelThree.updateGraph(samples[7]);
                                    Log.w(TAG, "Data Type: " + samples[0] + "Packet Number: " + samples[1] +
                                            " {Samples: " + samples[2] + "-" + samples[3] + "-" + samples[4] +
                                            "-" + samples[5] + "-" + samples[6] + "-" + samples[7] + "}");
                                }
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BleService.ONE_CHANNEL_PPG)) {
                final int[] samples = intent.getIntArrayExtra(BleService.EXTRA_DATA) ;
                (new Runnable(){
                    public void run(){
                        if (samples != null ){
                            if(mDataType < 0)
                                mDataType = samples[0];
                            else {
                                if (mDataRecording)
                                    mRecordedData.add((new DataPacket(samples)));
                                if (mShowGraph) {
                                    mChannelOne.updateGraph(samples[2]);
                                    mChannelOne.updateGraph(samples[3]);
                                    Log.w(TAG, "Data Type: " + samples[0] + "Packet Number: " + samples[1] +
                                            " {Samples: " + samples[2] + "-" + samples[3] + "-" + samples[4] +
                                            "-" + samples[5] + "-" + samples[6] + "-" + samples[7] + "}");
                                }
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BleService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY)) {
                final int[] samples = intent.getIntArrayExtra(BleService.EXTRA_DATA) ;
                (new Runnable(){
                    public void run(){
                        if (samples != null ){
                            if(mDataType < 0)
                                mDataType = samples[0];
                            else {
                                if (mDataRecording)
                                    mRecordedData.add((new DataPacket(samples)));
                                if (mShowGraph) {
                                    mChannelOne.updateGraph(samples[2]);
                                    mChannelOne.updateGraph(samples[3]);
                                    Log.w(TAG, "Data Type: " + samples[0] + "Packet Number: " + samples[1] +
                                            " {Samples: " + samples[2] + "-" + samples[3] + "-" + samples[4] +
                                            "-" + samples[5] + "-" + samples[6] + "-" + samples[7] + "}");
                                }
                            }
                        }
                    }
                }).run();
            }
            if (action.equals(BleService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }

            if(action.equals(BleService.ACTION_TX_CHAR_WRITE)){
                Log.d(TAG, "Write RX done");
            }

            if(action.equals(BleService.ACTION_SENSOR_POSITION_READ)){
                mSensorPos = intent.getStringExtra(BleService.EXTRA_DATA);
            }

            if(action.equals(BleService.ACTION_HEART_RATE_READ)){
                if(mShowGraph)
                    setHeartRateValue(intent.getIntExtra(BleService.EXTRA_DATA, 0));
            }
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BleService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).
                registerReceiver(BLEStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private void stopRecordingData(){
        if(mDataRecording) {
            saveToDisk();
            mDataRecording = false;
            btnStore.setText("Record");
            mHandler.removeCallbacks(mRecordTimer);
            ((TextView) findViewById(R.id.timer_view)).setText("");
            refreshTimer();
            if(mShowGraph)
                stopGraph();
        }
    }

    private void saveToDisk(){
        if(mRecordedData.isEmpty()){
            showMessage("No data recorded");
            return;
        }

        if(isExternalStorageWritable()){
            new Thread(new Runnable(){
                public void run(){
                    File root = android.os.Environment.getExternalStorageDirectory();
                    File dir = new File (root.getAbsolutePath() + DIRECTORY_NAME);
                    if(!dir.isDirectory())
                        dir.mkdirs();
                    File file;
                    String fileName = ecgM.getSensor()+"_"+ecgM.getTimeStamp()+".txt";
                    file = new File(dir, fileName);
                    Type type = new TypeToken<ArrayList<DataPacket>>() {}.getType();
                    ecgM.setData(new Gson().toJson(mRecordedData, type));
                    mRecordedData.clear();
                    try {
                        FileWriter fw = new FileWriter(file, true);
                        fw.append(ecgM.toJson());
                        ecgM = null;
                        fw.flush();
                        fw.close();
                        showMessage("ECG Record Saved");
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                        showMessage("Problem writing to Storage");
                    }
                }
            }).start();
        }
        else
            showMessage("Cannot write to storage");
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private Runnable mRecordTimer = new Runnable() {
        @Override
        public void run() {
            if(!mRecordedData.isEmpty()) {
                if (mRecTimerCounter < SECONDS_IN_ONE_MINUTE) {
                    sec = mRecTimerCounter;
                } else if (mRecTimerCounter < SECONDS_IN_ONE_HOUR) {
                    min = mRecTimerCounter / SECONDS_IN_ONE_MINUTE;
                    sec = mRecTimerCounter % SECONDS_IN_ONE_MINUTE;
                } else {
                    hr = mRecTimerCounter / SECONDS_IN_ONE_HOUR;
                    min = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) / SECONDS_IN_ONE_MINUTE;
                    min = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) % SECONDS_IN_ONE_MINUTE;
                }
                updateTimer();
                if (mRecTimerCounter >= MAX_DATA_RECORDING_TIME) {
                    stopRecordingData();
                    if (mShowGraph) {
                        stopGraph();
                    }
                    return;
                }
                if ((MAX_DATA_RECORDING_TIME - mRecTimerCounter) < 5)//Five seconds to the end of timer
                    ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.green));
                mRecTimerCounter++;
            }
            mHandler.postDelayed(mRecordTimer, ONE_SECOND);
        }
    };

    private void refreshTimer(){
        mRecTimerCounter = 1;
        hr = min = sec = 0;
        ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.red));
    }

    private void updateTimer(){
        mTimerString = String.format("%02d:%02d:%02d", hr,min,sec);
        ((TextView) findViewById(R.id.timer_view)).setText(mTimerString);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_RX_DATA_AVAILABLE);
        intentFilter.addAction(BleService.DEVICE_DOES_NOT_SUPPORT_UART);
        intentFilter.addAction(BleService.ACTION_TX_CHAR_WRITE);
        intentFilter.addAction(BleService.ACTION_SENSOR_POSITION_READ);
        intentFilter.addAction(BleService.ACTION_HEART_RATE_READ);
        intentFilter.addAction(BleService.ONE_CHANNEL_ECG);
        intentFilter.addAction(BleService.THREE_CHANNEL_ECG);
        intentFilter.addAction(BleService.ONE_CHANNEL_PPG);
        intentFilter.addAction(BleService.TWO_CHANNEL_PPG);
        intentFilter.addAction(BleService.THREE_CHANNEL_ACCELERATION);
        intentFilter.addAction(BleService.ONE_CHANNEL_IMPEDANCE_PNEUMOGRAPHY);
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
            LocalBroadcastManager.getInstance(this).unregisterReceiver(BLEStatusChangeReceiver);
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
        if (mShowGraph) {
            stopGraph();
        }
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
                    showMessage("Bluetooth has turned on ");

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    showMessage("Problem in BT Turning ON ");
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

    private boolean setGraphLayout(){
        if(mDataType < 0 || mDataType > 5)
            return false;
        FragmentManager fm = getFragmentManager();
        switch (mDataType){
            case 0:
            case 2:
            case 5:
                if(fm.findFragmentByTag("chOne") == null) {
                    fm.beginTransaction()
                            .add(R.id.channel1_fragment, mChannelOne, "chOne")
                            .commit();
                }
                break;
            case 1:
            case 4:
                if(fm.findFragmentByTag("chOne") == null) {
                    fm.beginTransaction()
                            .add(R.id.channel1_fragment, mChannelOne, "chOne")
                            .commit();
                }
                if(fm.findFragmentByTag("chTwo") == null) {
                    fm.beginTransaction()
                            .add(R.id.channel2_fragment, mChannelTwo, "chTwo")
                            .commit();
                }
                if(fm.findFragmentByTag("chThree") == null) {
                    fm.beginTransaction()
                            .add(R.id.channel3_fragment, mChannelThree, "chThree")
                            .commit();
                }
                break;
            case 3:
                if(fm.findFragmentByTag("chOne") == null) {
                    fm.beginTransaction()
                            .add(R.id.channel1_fragment, mChannelOne, "chOne")
                            .commit();
                }
                if(fm.findFragmentByTag("chTwo") == null) {
                    fm.beginTransaction()
                            .add(R.id.channel2_fragment, mChannelTwo, "chTwo")
                            .commit();
                }
                break;
        }
        return true;
    }
    private void clearGraphLayout(){
        FragmentManager fm = getFragmentManager();
        switch (mDataType) {
            case 0:
            case 2:
            case 5:
                mChannelOne.clearGraph();
                if(fm.findFragmentByTag("chOne") != null) {
                    fm.beginTransaction()
                            .remove(mChannelOne)
                            .commit();
                }
                break;
            case 1:
                mChannelOne.clearGraph();
                mChannelTwo.clearGraph();
                mChannelThree.clearGraph();
                if(fm.findFragmentByTag("chOne") != null) {
                    fm.beginTransaction()
                            .remove(mChannelOne)
                            .commit();
                }
                if(fm.findFragmentByTag("chTwo") != null) {
                    fm.beginTransaction()
                            .remove(mChannelTwo)
                            .commit();
                }
                if(fm.findFragmentByTag("chThree") != null) {
                    fm.beginTransaction()
                            .remove(mChannelThree)
                            .commit();
                }
                break;
            case 3:
            case 4:
                mChannelOne.clearGraph();
                mChannelTwo.clearGraph();
                if(fm.findFragmentByTag("chOne") != null) {
                    fm.beginTransaction()
                            .remove(mChannelOne)
                            .commit();
                }
                if(fm.findFragmentByTag("chTwo") != null) {
                    fm.beginTransaction()
                            .remove(mChannelTwo)
                            .commit();
                }
                break;
        }
        mDataType = -1;
    }

    private void showMessage(final String msg) {
        Runnable showMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };
        mHandler.post(showMessage);

    }
}
