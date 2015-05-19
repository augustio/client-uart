package electria.electriahrm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;


public class HistoryDetail extends Activity {

    private static final String TAG = HistoryDetail.class.getSimpleName();
    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ViewGroup historyViewLayout;
    private String filePath;
    private int mCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);
        setGraphView();
        mCounter = 0;
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        filePath = extras.getString(Intent.EXTRA_TEXT);
        readFromDisk(filePath);
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = new LineGraphView();
        mGraphView = mLineGraph.getView(this);
        historyViewLayout = (ViewGroup) findViewById(R.id.history_detail);
        historyViewLayout.addView(mGraphView);
    }

    //Read data from phone storage
    private void readFromDisk(String fName) {
        if (isExternalStorageReadable()) {
            File root = android.os.Environment.getExternalStorageDirectory();
            try {
                BufferedReader buf = new BufferedReader(new FileReader(root.getAbsolutePath() + fName));
                String readString = buf.readLine ( ) ;
                while ( readString != null && mCounter < 3000 ) {
                   updateGraph(readString.replaceAll("\\s+",""));
                    readString = buf.readLine ( ) ;
                }
                buf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else
            Log.w(TAG, "External storage not readable");
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

    //Plot a new set of values on the graph and present on the GUI
    private void updateGraph(String str){
        double maxX = mCounter;
        double minX =  (maxX < 500) ? 0 : (maxX - 500);
        mLineGraph.setRange(minX, maxX, 200, 700);
        mLineGraph.addValue(new Point(mCounter, Integer.parseInt(str)));
        mGraphView.repaint();
        mCounter += 2;
    }
}
