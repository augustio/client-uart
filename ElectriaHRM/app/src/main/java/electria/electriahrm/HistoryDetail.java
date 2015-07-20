package electria.electriahrm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.achartengine.GraphicalView;
import org.apache.http.HttpEntity;
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
    private static final String SUCCESS = "File Access Successful";
    private static final String SERVER_ERROR = "No Response From Server!";
    private static final String NO_NETWORK_CONNECTION = "Not Connected to Network";
    private static final String CONNECTION_ERROR= "Server Not Reachable, Check Internet Connection";
    private static final String SERVER_URL = "http://52.18.112.240:3000/records";
    private static final int X_RANGE = 500;
    private static final int MIN_Y = 0;//Minimum ECG data value
    private static final int MAX_Y = 1023;//Maximum ECG data value
    private static final int MIN_X = 0;
    private static final int MAX_X = 20000;
    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ViewGroup mHistLayout;
    private Button btnSendEmail, btnSendCloud;
    private TextView accessStatus;
    private String mFPath;
    private File mFile;
    private List<String> mCollection;
    private ECGMeasurement ecgM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);
        mCollection = new ArrayList<String>();
        btnSendEmail = (Button)findViewById(R.id.send_email);
        btnSendCloud = (Button)findViewById(R.id.send_cloud);
        accessStatus = (TextView)findViewById(R.id.server_access_status);
        btnSendEmail.setEnabled(false);
        btnSendCloud.setEnabled(false);
        mFile = null;
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        mFPath = extras.getString(Intent.EXTRA_TEXT);
        if(isExternalStorageReadable()){
            if((mFile = validateFile(mFPath))  != null){
                setGraphView();
                new DisplayECGTask().execute(mFile);
            }
            else{
                finish();
            }
        }
        else{
            showMessage("Cannot read from storage");
            finish();
        }

        btnSendEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hasNetworkConnection())
                    // call AsynTask to perform network operation on separate thread
                    sendAttachment();
                else
                    showMessage(NO_NETWORK_CONNECTION);
            }
        });

        btnSendCloud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hasNetworkConnection())
                    // call AsynTask to perform network operation on separate thread
                    new HttpAsyncTask().execute(SERVER_URL);
                else
                    showMessage(NO_NETWORK_CONNECTION);
            }
        });
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = new LineGraphView();
        mLineGraph.setYRange(MIN_Y, MAX_Y);
        mLineGraph.setPanLimits(MIN_X, MAX_X, MIN_Y, MAX_Y);
        mGraphView = mLineGraph.getView(this);
        mHistLayout = (ViewGroup) findViewById(R.id.history_detail);
        mHistLayout.addView(mGraphView);
    }

    private class DisplayECGTask extends AsyncTask<File, Integer, String> {

        private Exception exception;
        private int xValue = 0;

        @Override
        protected String doInBackground(File... mFiles) {
            try {
                BufferedReader buf = new BufferedReader(new FileReader(mFiles[0]));
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
                showMessage("Problem accessing mFile");
            }
            else {
                Log.d(TAG, SUCCESS);
                btnSendEmail.setEnabled(true);
                btnSendCloud.setEnabled(true);
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

    //Add a point to the graph
    private void updateGraph(int x, int y){
        double maxX = x;
        double minX = (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
        mLineGraph.setXRange(minX, maxX);
        mLineGraph.addValue(new Point(x, y));
        mGraphView.repaint();
    }

    public static String POST(String url, ECGMeasurement ecgM){
        InputStream inputStream;
        String result;
        try {
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url);
            String json;

            JSONObject jsonObject = new JSONObject();
            jsonObject.accumulate("sensor", ecgM.getId());
            jsonObject.accumulate("timestamp", ecgM.getTimeStamp());
            jsonObject.accumulate("data", ecgM.getData());

            json = jsonObject.toString();

            StringEntity se = new StringEntity(json);
            httpPost.setEntity(se);

            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            HttpResponse httpResponse = httpclient.execute(httpPost);

            HttpEntity httpEntity = httpResponse.getEntity();

            if(httpEntity != null){
                inputStream = httpEntity.getContent();
                result = convertInputStreamToString(inputStream);
            }else
                result = SERVER_ERROR;

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
            result =  CONNECTION_ERROR;
        }

        return result;
    }

    private class HttpAsyncTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... urls) {

            ecgM = new ECGMeasurement();
            ecgM.setId(mFPath.substring(mFPath.lastIndexOf("/") + 1, mFPath.lastIndexOf("_")));
            ecgM.setTimeStamp(mFPath.substring(mFPath.lastIndexOf("_") + 1, mFPath.lastIndexOf(".")));
            ecgM.setData(Arrays.toString(mCollection.toArray(new String[mCollection.size()])));

            publishProgress("Sending Data to Server ...");
            return POST(urls[0], ecgM);
        }

        protected void onProgressUpdate(String... value) {
            accessStatus.setText(value[0]);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            accessStatus.setText("");
            if (result.equals(SERVER_ERROR))
                showMessage("Error Connecting to Server");
            else if (result.equals(CONNECTION_ERROR))
                showMessage(CONNECTION_ERROR);
            else
                showMessage("Data Sent");

            finish();
        }

    }

    //Send ECG data as attachment to a specified Email address
    private void sendAttachment(){
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "ECG Data");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Attached is a copy of ECG samples");
        emailIntent.setData(Uri.parse("mailto:electria.metropolia@gmail.com"));
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse( "file://"+mFPath));

        try {
            startActivity(Intent.createChooser(emailIntent, "Sending Email...."));
        }catch (android.content.ActivityNotFoundException ex) {
            showMessage("No email clients installed.");
        }
        finish();
    }

    /*Checks if mFile is a text mFile and is not empty*/
    private File validateFile(String path){
        File f = null;
        if(path.endsWith(("txt"))){
            f = new File(path);
            //File is considered empty if less than or equal to the size of a character
            if(f.length() <= Character.SIZE) {
                showMessage("Empty File");
                return null;
            }
        }
        else
            showMessage("Invalid File Format");
        return f;
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

    public boolean hasNetworkConnection(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
