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
}
