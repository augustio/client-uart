package electria.electriahrm;

import android.app.Activity;;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


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

    private void readFromDisk(String fName) {
        if (isExternalStorageReadable()) {
            File root = android.os.Environment.getExternalStorageDirectory();
            try {
                BufferedReader buf = new BufferedReader(new FileReader(root.getAbsolutePath() + fName));
                String readString = buf.readLine ( ) ;
                while ( readString != null ) {
                    readString = readString.replaceAll("\\s+","");
                    double maxX = mCounter;
                    double minX =  (maxX < 500) ? 0 : (maxX - 500);
                    mLineGraph.setRange(minX, maxX, 0, 1023);
                    mLineGraph.addValue(new Point(mCounter, Integer.parseInt(readString)));
                    mGraphView.repaint();
                    mCounter += 2;
                    readString = buf.readLine ( ) ;
                }
                buf.close();
                Log.d(TAG, "Done reading");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.i(TAG, "******* File not found. Did you" +
                        " add a WRITE_EXTERNAL_STORAGE permission to the   manifest?");
            } catch (IOException e) {
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
}
