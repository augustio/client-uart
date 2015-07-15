package electria.electriahrm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.achartengine.GraphicalView;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class HistoryDetail extends Activity {

    private static final String TAG = HistoryDetail.class.getSimpleName();
    private static final String SUCCESS = "Operation Successful";
    private static final String SERVER_ERROR = "No Response From Server!";
    private static final String SERVER_EXCEPTION = "Exception from Server Access";
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
    private ECGMeasurement ecgM;

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

    public static String POST(String url, ECGMeasurement ecgM){
        InputStream inputStream = null;
        String result = "";
        try {
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url);
            String json = "";

            JSONObject jsonObject = new JSONObject();
            jsonObject.accumulate("sensor", ecgM.getId());
            jsonObject.accumulate("timestamp", ecgM.getTimeStamp());
            jsonObject.accumulate("data", ecgM.getData());

            json = jsonObject.toString();
            Log.w(TAG, "Json String: " + json);

            StringEntity se = new StringEntity(json);
            httpPost.setEntity(se);

            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            HttpResponse httpResponse = httpclient.execute(httpPost);

            inputStream = httpResponse.getEntity().getContent();

            if(inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = SERVER_ERROR;

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
            result =  SERVER_EXCEPTION;
        }

        return result;
    }

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            ecgM = new ECGMeasurement();
            ecgM.setId(filePath.substring(filePath.lastIndexOf("/") + 1, filePath.lastIndexOf("_")));
            ecgM.setTimeStamp(filePath.substring(filePath.lastIndexOf("_") + 1, filePath.lastIndexOf(".")));
            ecgM.setData(Arrays.toString(mCollection.toArray(new String[mCollection.size()])));

            return POST(urls[0], ecgM);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            if (result.equals(SERVER_ERROR))
                showMessage("Error Connecting to Server");
            else if (result.equals(SERVER_EXCEPTION))
                showMessage("Data not Sent");
            else
                showMessage("Data Sent");
        }

    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
