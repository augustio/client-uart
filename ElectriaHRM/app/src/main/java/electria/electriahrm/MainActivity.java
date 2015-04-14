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

    private boolean isGraphInProgress;
    private boolean btnPlotClicked;
    private boolean btnLogClicked;
    private boolean startGraphUpdate;
    private boolean onPause;
    private boolean startLogging;
    private boolean isLogging;

    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ListView messageListView;
    private ArrayAdapter<String> listAdapter;
    private TextView batLevelView, tempView;
    private EditText edtMessage;
    private Button btnConnectDisconnect,btnPlot,btnPause,btnLog,btnSend;
    private ViewGroup mainLayout;

    private int mCounter = 0;
    private int hrmValue1 = 0;
    private int hrmValue2 = 0;
    private int batteryValue = 0;
    private String mDeviceAddress = null;
    private UartService mService = null;
    private String mHRM = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private int mState = UART_PROFILE_DISCONNECTED;

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
        btnConnectDisconnect.setBackgroundColor(0X7700FF00);
        btnPlot=(Button) findViewById(R.id.btn_plot);
        btnPlot.setBackgroundColor(0X770000FF);
        btnPause=(Button) findViewById(R.id.btn_pause);
        btnPause.setBackgroundColor(0X77FFFF00);
        btnLog=(Button) findViewById(R.id.btn_log);
        btnLog.setBackgroundColor(0X770000FF);
        btnSend=(Button) findViewById(R.id.sendButton);
        edtMessage=(EditText) findViewById(R.id.sendText);
        messageListView = (ListView) findViewById(R.id.listMessage);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        batLevelView = (TextView) findViewById(R.id.bat_level);
        tempView = (TextView) findViewById(R.id.temp);

        setGUI();
        service_init();
        initFlags();
    }

    //Plot two new sets of values on the graph and present on the GUI
    private void updateGraph() {
        double maxX = mCounter+=10;
        double minX =  (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
        mLineGraph.setRange(minX, (maxX), 0, 1023);
        mLineGraph.addValue(new Point(mCounter, hrmValue1));
        mLineGraph.addValue(new Point(mCounter, hrmValue2));
        mGraphView.repaint();
    }

    //Display received ECG data on GUI
    private void logData(){
        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
        listAdapter.add('[' +currentDateTimeString+']'+ '\n' +mHRM);
        messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
    }

    private void updateBatteryLevel(int level) {
        batteryValue = level;
        batLevelView.setText("Battery Level: " + batteryValue + "%");
    }

    private void updateTemp(double temp) {
        temp = (temp/4.0);
        tempView.setText("Temp: " + temp + "°C");
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

    private void clearLog(){
        if(isLogging) {
            isLogging = false;
            startLogging = false;
            btnLogClicked = false;
            messageListView.setAdapter(null);
        }
    }

    private void resetGUI(){
        clearLog();
        clearGraph();
        btnPause.setBackgroundColor(0X77FFFF00);
        btnPause.setText("Pause");
        batLevelView.setText(R.string.batteryLevel);
        tempView.setText(R.string.temperature);
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
        startLogging = false;
        isLogging = false;
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
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

            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "UART_CONNECT_MSG");
                        btnConnectDisconnect.setText("Disconnect");
                        btnConnectDisconnect.setBackgroundColor(0X77FF0000);
                        edtMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        btnConnectDisconnect.setText("Connect");
                        btnConnectDisconnect.setBackgroundColor(0X7700FF00);
                        resetGUI();
                        initFlags();
                        ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                        if(mDeviceAddress != null)
                            mService.connect(mDeviceAddress);
                        else {
                            mState = UART_PROFILE_DISCONNECTED;
                            mService.close();
                        }
                    }
                });
            }

            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableRXNotification();
            }

            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                final byte[] rxValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (rxValue != null){
                            processRXData(rxValue);
                            if (startGraphUpdate) {
                                updateGraph();
                            }
                            if (startLogging) {
                                logData();
                            }
                        }
                    }
                });

            }

            if(action.equals(UartService.BATTERY_VALUE_READ)){
                final int batValue = mService.getBatteryValue();
                runOnUiThread(new Runnable() {
                    public void run() {
                        updateBatteryLevel(batValue);
                    }
                });
            }

            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }

        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private void processRXData(byte[] rxValue){
        String[] str;
        try {
            String rxString = new String(rxValue, "UTF-8");
            if(rxString.contains("-")){
                str = rxString.split("-");
                if(str.length == 3){//str will contain 3 strings if temp value is sent
                    updateTemp(Integer.parseInt(str[0]));
                    hrmValue1 = Integer.parseInt(str[1]);
                    hrmValue2 = Integer.parseInt(str[2]);
                }
                else{
                    hrmValue1 = Integer.parseInt(str[0]);
                    hrmValue2 = Integer.parseInt(str[1]);
                }
                mHRM = hrmValue1+"\n"+hrmValue2;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        intentFilter.addAction(UartService.BATTERY_VALUE_READ);
        return intentFilter;
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }
}
