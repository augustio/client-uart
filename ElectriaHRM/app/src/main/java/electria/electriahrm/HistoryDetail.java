package electria.electriahrm;

import android.app.Activity;;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


public class HistoryDetail extends Activity {

    private static final String TAG = HistoryDetail.class.getSimpleName();

    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ViewGroup historyViewLayout;

    private String ecgDataArray[];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);

        ecgDataArray = null;
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = LineGraphView.getLineGraphView();
        mGraphView = mLineGraph.getView(this);
        historyViewLayout = (ViewGroup) findViewById(R.id.history_detail);
        historyViewLayout.addView(mGraphView);
    }

    private void readFromDisk(String fName) {
        if (isExternalStorageReadable()) {
            File root = android.os.Environment.getExternalStorageDirectory();
            File file = new File(root.getAbsolutePath() + fName);
            String ecgData = null;
            try {
                FileReader fr = new FileReader(file);
                fr.read();
                ecgData = fr.toString();
                fr.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.i(TAG, "******* File not found. Did you" +
                        " add a WRITE_EXTERNAL_STORAGE permission to the   manifest?");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (ecgData != null) {
                ecgDataArray = ecgData.split(" ");
                Log.d(TAG, "File read");
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
