package electria.electriahrm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.achartengine.GraphicalView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;


public class HistoryDetail extends Activity {

    private static final String TAG = HistoryDetail.class.getSimpleName();
    private static final int MAX_DATA_TO_DISPLAY = 5000; //Max data to show on graph
    private static final int X_RANGE = 500;
    private static final int DEFAULT_MIN_Y = 0;//Minimum ECG data value
    private static final int DEFAULT_MAX_Y = 1023;//Maximum ECG data value
    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ViewGroup historyViewLayout;
    private Button btnSend;
    private String filePath;
    private int mCounter, mCollectionIndex, minY, maxY;
    private Handler mHandler;
    private List<Integer> mCollection;
    private boolean isFileEmpty, isFileFormatInvalid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);
        mCollection = new ArrayList<Integer>();
        btnSend = (Button)findViewById(R.id.send_data);
        mHandler = new Handler();
        maxY = DEFAULT_MIN_Y;
        minY = DEFAULT_MAX_Y;
        mCounter = mCollectionIndex = 0;
        isFileEmpty = isFileFormatInvalid = false;
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        filePath = extras.getString(Intent.EXTRA_TEXT);
        readFromDisk();
        if(isFileEmpty){
            showMessage("Empty File");
            finish();
        }
        if(isFileFormatInvalid){
            showMessage("Invalid File Format");
            finish();
        }
        setGraphView();
        mDisplayGraph.run();//Initiate graph display and update

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    sendAttachment();
            }
        });
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = new LineGraphView();
        mLineGraph.setYRange(minY, maxY);
        mLineGraph.setPanLimits(0, MAX_DATA_TO_DISPLAY, minY, maxY);
        mGraphView = mLineGraph.getView(this);
        historyViewLayout = (ViewGroup) findViewById(R.id.history_detail);
        historyViewLayout.addView(mGraphView);
    }

    //Read data from phone storage
    private void readFromDisk() {
        if(!filePath.endsWith(("txt"))){
            isFileFormatInvalid = true;
            return;
        }
        if (isExternalStorageReadable()) {
            try {
                File f = new File(filePath);
                if(f.length() <= Character.SIZE) {
                    isFileEmpty = true;
                    return;
                }
                BufferedReader buf = new BufferedReader(new FileReader(f));
                String line;
                int value;
                while ( (line = buf.readLine()) != null && mCollection.size() < MAX_DATA_TO_DISPLAY ){
                    if(android.text.TextUtils.isDigitsOnly(line)) {
                        value = Integer.parseInt(line);
                        mCollection.add(value);
                        if(value < minY)
                            minY = value;
                        if(value > maxY)
                            maxY = value;
                    }
                }
                buf.close();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                showMessage("Problem reading from Storage");
            }
        } else
            showMessage("Cannot read from storage");
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    //Updates graph with values in the collection
    private Runnable mDisplayGraph = new Runnable() {
        @Override
        public void run() {
            if (mCollectionIndex < mCollection.size()){
                updateGraph(mCollection.get(mCollectionIndex));
                mCollectionIndex++;
                mHandler.post(mDisplayGraph);
            }
        }
    };

    //Add a point to the graph
    private void updateGraph(int value){
        double maxX = mCounter+1;
        double minX = (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
        mLineGraph.setXRange(minX, maxX);
        mLineGraph.addValue(new Point(mCounter, value));
        mGraphView.repaint();
        mCounter ++;
    }

    //Send ECG data as attachment to a specified Email address
    private void sendAttachment(){
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "ECG Data");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Attached is a copy of ECG samples");
        emailIntent.setData(Uri.parse("mailto:electria.metropolia@gmail.com"));
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse( "file://"+filePath));

        try {
            startActivity(Intent.createChooser(emailIntent, "Sending Email...."));
        }catch (android.content.ActivityNotFoundException ex) {
            showMessage("No email clients installed.");
        }
        finish();
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
