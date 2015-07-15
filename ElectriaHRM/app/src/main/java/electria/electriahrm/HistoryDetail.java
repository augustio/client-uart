package electria.electriahrm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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
    private static final String SUCCESS = "Operation Successful";
    private static final int X_RANGE = 500;
    private static final int MIN_Y = 0;//Minimum ECG data value
    private static final int MAX_Y = 1023;//Maximum ECG data value
    private static final int MIN_X = 0;
    private static final int MAX_X = 20000;
    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ViewGroup historyViewLayout;
    private Button btnSend;
    private String filePath;
    private File file;
    private List<String> mCollection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);
        mCollection = new ArrayList<String>();
        btnSend = (Button)findViewById(R.id.send_data);
        btnSend.setEnabled(false);
        file = null;
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        filePath = extras.getString(Intent.EXTRA_TEXT);
        if(isExternalStorageReadable()){
            validateFile(filePath);
            if(file != null){
                setGraphView();
                new DisplayECGTask().execute(file);
            }
            else{
                finish();
            }
        }
        else{
            showMessage("Cannot read from storage");
            finish();
        }

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
        mLineGraph.setYRange(MIN_Y, MAX_Y);
        mLineGraph.setPanLimits(MIN_X, MAX_X, MIN_Y, MAX_Y);
        mGraphView = mLineGraph.getView(this);
        historyViewLayout = (ViewGroup) findViewById(R.id.history_detail);
        historyViewLayout.addView(mGraphView);
    }

    private class DisplayECGTask extends AsyncTask<File, Integer, String> {

        private Exception exception;
        private int xValue = 0;

        @Override
        protected String doInBackground(File... files) {
            try {
                BufferedReader buf = new BufferedReader(new FileReader(files[0]));
                String line;
                while ( (line = buf.readLine()) != null){
                    if(android.text.TextUtils.isDigitsOnly(line)) {
                        mCollection.add(line);
                        publishProgress(Integer.parseInt(line));
                    }
                }
                buf.close();
            } catch (Exception e) {
                exception = e;
                return null;
            }
            return SUCCESS;
        }

        /*Displays ECG data on the graph*/
        protected void onProgressUpdate(Integer... value) {
            int yValue = value[0];
            updateGraph(xValue, yValue);
            xValue++;
        }

        /*Handles any exception and reports result of operation*/
        @Override
        protected void onPostExecute(String result) {
            if(exception != null){
                Log.e(TAG, exception.toString());
                showMessage("Problem accessing file");
            }
            else {
                Log.d(TAG, SUCCESS);
                btnSend.setEnabled(true);
            }
        }
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

    /*Checks if file is a text file and is not empty*/
    private void validateFile(String path){
        if(path.endsWith(("txt"))){
            file = new File(path);
            //File is considered empty if less than or equal to the size of a character
            if(file.length() <= Character.SIZE) {
                showMessage("Empty File");
                file = null;
            }
        }
        else
            showMessage("Invalid File Format");
    }

    //Add a point to the graph
    private void updateGraph(int x, int y){
        double maxX = x;
        double minX = (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
        mLineGraph.setXRange(minX, maxX);
        mLineGraph.addValue(new Point(x, y));
        mGraphView.repaint();
    }

    //Send ECG data as attachment to a specified Email address
    private void sendAttachment(){
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
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
