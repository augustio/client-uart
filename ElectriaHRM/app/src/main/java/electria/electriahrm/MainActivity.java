package electria.electriahrm;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;


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
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.graphics.Point;

import org.achartengine.GraphicalView;


public class MainActivity extends Activity {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int X_RANGE = 500;
    private static final int DEFAULT_BATTERY_LEVEL = 0;

    private boolean isGraphInProgress;
    private boolean btnPlotClicked;
    private boolean btnLogClicked;
    private boolean startGraphUpdate;
    private boolean onPause;

    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ListView messageListView;
    private ArrayAdapter<String> listAdapter;
    private TextView batLevelView;
    private EditText edtMessage;
    private Button btnConnectDisconnect,btnPlot,btnPause,btnLog,btnSend;
    private ViewGroup mainLayout;

    private int mCounter = 0;
    private int hrmValue1 = 0;
    private int hrmValue2 = 0;
    private int lastBatLevel = 0;
    private BleService mService = null;
    private String mHRM = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private int mState = UART_PROFILE_DISCONNECTED;
    private int packetNumber;

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

        btnConnectDisconnect=(Button) findViewById(R.id.btn_select);
        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.green));
        btnPlot=(Button) findViewById(R.id.btn_plot);
        btnPlot.setBackgroundColor(getResources().getColor(R.color.blue));
        btnPause=(Button) findViewById(R.id.btn_pause);
        btnPause.setBackgroundColor(getResources().getColor(R.color.yellow));
        btnSend=(Button) findViewById(R.id.sendButton);
        edtMessage=(EditText) findViewById(R.id.sendText);
        batLevelView = (TextView) findViewById(R.id.bat_level);

        setGUI();
        service_init();
        initFlags();

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
                    if (btnConnectDisconnect.getText().equals("Connect")){
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        resetGUI();
                        initFlags();
                        //Disconnect button pressed
                        if (mDevice!=null)
                        {
                            mService.disconnect();

                        }
                    }
                }
            }
        });

        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.sendText);
                String message = editText.getText().toString();
                byte[] value;
                try {
                    //send data to service
                    value = message.getBytes("UTF-8");
                    mService.writeTXCharacteristic(value);
                    edtMessage.setText("");
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        });

        // Handle Plot Graph button
        btnPlot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!btnConnectDisconnect.getText().equals("Connect")) {
                    if (!btnPlotClicked && !onPause) {
                        if (mState == UART_PROFILE_CONNECTED) {
                            btnPlotClicked = true;
                            startGraphUpdate = true;
                            isGraphInProgress = true;
                            mainLayout.addView(mGraphView);
                        }
                    }
                }
            }
        });

        // Handle Pause Graph button
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!btnConnectDisconnect.getText().equals("Connect")) {
                    if (btnPlotClicked) {
                        if (startGraphUpdate) {
                            btnPause.setBackgroundColor(getResources().getColor(R.color.green));
                            btnPause.setText("Resume");
                            startGraphUpdate = false;
                            onPause = true;
                        } else {
                            startGraphUpdate = true;
                            onPause = false;
                            btnPause.setBackgroundColor(getResources().getColor(R.color.yellow));
                            btnPause.setText("Pause");
                        }
                    }
                }
            }
        });

    }

    //Plot two new sets of values on the graph and present on the GUI
    private void updateGraph(int hrmValue1, int hrmValue2) {
        double maxX = mCounter+=5;
        double minX =  (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
        mLineGraph.setRange(minX, maxX, 0, 1023);
        mLineGraph.addValue(new Point(mCounter, hrmValue1));
        mLineGraph.addValue(new Point(mCounter, hrmValue2));
        mGraphView.repaint();
    }

    private void updateBatteryLevel(int level) {
        if(lastBatLevel != level)
            lastBatLevel = level;
        batLevelView.setText("Battery Level: " + lastBatLevel + "%");
    }

    private void clearGraph() {
        if(isGraphInProgress) {
            startGraphUpdate = false;
            isGraphInProgress = false;
            btnPlotClicked = false;
            mLineGraph.clearGraph();
            mGraphView.repaint();
            mCounter = 0;
            mainLayout.removeView(mGraphView);
        }
    }

    private void resetGUI(){
        clearGraph();
        btnPause.setBackgroundColor(getResources().getColor(R.color.yellow));
        btnPause.setText("Pause");
        batLevelView.setText(R.string.batteryLevel);
    }

    //Prepare the initial GUI for graph
    private void setGUI() {
        mLineGraph = LineGraphView.getLineGraphView();
        mGraphView = mLineGraph.getView(this);
        mainLayout = (ViewGroup) findViewById(R.id.linearLayout3);
    }

    private void initFlags(){
        isGraphInProgress = false;
        btnPlotClicked = false;
        btnLogClicked = false;
        startGraphUpdate = false;
        onPause = false;
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
                        edtMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            if (action.equals(BleService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        btnConnectDisconnect.setText("Connect");
                        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.green));
                        resetGUI();
                        initFlags();
                        ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                    }
                });
            }

            if (action.equals(BleService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableRXNotification();
            }

            if (action.equals(BleService.ACTION_RX_DATA_AVAILABLE)) {
                final byte[] rxValue = intent.getByteArrayExtra(BleService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (rxValue != null){
                            processRXData(rxValue);
                        }
                    }
                });

            }

            else if(action.equals(BleService.ACTION_BATTERY_LEVEL_DATA_AVAILABLE)){
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

        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BleService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private void processRXData(byte[] rxValue){
        String[] str;
        int pNum;

        try {
            String rxString = new String(rxValue, "UTF-8");
            str = rxString.split("-");
            pNum = Integer.parseInt(str[3]);
            if(packetNumber == 100)
                packetNumber = 0;
            else if(packetNumber == 0)
                packetNumber = pNum;
            else
                packetNumber++;
            if((pNum - packetNumber) >= 5) {
                Log.w(TAG, "Lost Packets: " + (pNum - packetNumber));
                return;
            }
            Log.d(TAG, "Packets: " + packetNumber + "---" + pNum);
            if(startGraphUpdate)
                updateGraph(Integer.parseInt(str[1]),Integer.parseInt(str[2]));
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_RX_DATA_AVAILABLE);
        intentFilter.addAction(BleService.ACTION_BATTERY_LEVEL_DATA_AVAILABLE);
        intentFilter.addAction(BleService.DEVICE_DOES_NOT_SUPPORT_UART);
        intentFilter.addAction(BleService.BATTERY_VALUE_READ);
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
        resetGUI();
        initFlags();
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
    public void finish() {
        mService.close();
        mDevice = null;
        super.finish();
    }

    @Override
    public void onBackPressed() {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }
}
