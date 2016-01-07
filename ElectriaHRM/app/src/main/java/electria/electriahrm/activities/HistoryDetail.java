package electria.electriahrm.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;

import electria.electriahrm.dataPackets.EcgThreeChannelsPacket;
import electria.electriahrm.fragments.Channel1Fragment;
import electria.electriahrm.fragments.Channel2Fragment;
import electria.electriahrm.fragments.Channel3Fragment;
import electria.electriahrm.R;
import electria.electriahrm.measurements.ECGMeasurement;

public class HistoryDetail extends Activity {

    private static final String TAG = HistoryDetail.class.getSimpleName();
    private String mFPath;
    private int mIndex;
    private File mFile;
    private ECGMeasurement ecgM;
    private Handler mHandler;
    private ArrayList<EcgThreeChannelsPacket> mDataCollection;

    private Channel1Fragment ecgChannelOne;
    private Channel2Fragment ecgChannelTwo;
    private Channel3Fragment ecgChannelThree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);
        mFile = null;
        mIndex = 0;
        ecgM = new ECGMeasurement();
        mHandler = new Handler();

        ecgChannelOne = (Channel1Fragment)getFragmentManager()
                .findFragmentById(R.id.channel1_fragment);
        ecgChannelTwo = (Channel2Fragment)getFragmentManager()
                .findFragmentById(R.id.channel2_fragment);
        ecgChannelThree = (Channel3Fragment)getFragmentManager()
                .findFragmentById(R.id.channel3_fragment);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }else {
            mFPath = extras.getString(Intent.EXTRA_TEXT);
        }
        if(isExternalStorageReadable()){
            if((mFile = validateFile(mFPath))  != null){
                getData();
                mDisplayGraph.run();
            }
            else{
                finish();
            }
        }
        else{
            showMessage("Cannot read from storage");
            finish();
        }
    }

    private void getData(){
        try {
            BufferedReader buf = new BufferedReader(new FileReader(mFile));
            ecgM.fromJson(buf.readLine());
            buf.close();
        }catch (Exception e) {
            Log.e(TAG, e.toString());
            showMessage("Problem accessing mFile");
        }
        Type type = new TypeToken<ArrayList<EcgThreeChannelsPacket>>() {}.getType();
        mDataCollection = new Gson().fromJson(ecgM.getData(), type);
    }

    private Runnable mDisplayGraph = new Runnable() {
        @Override
        public void run() {
            switch (mDataCollection.get(0).getDataId()) {
                case 1:
                    if (mIndex < mDataCollection.size()) {
                        EcgThreeChannelsPacket pkt = mDataCollection.get(mIndex);
                        ecgChannelOne.updateGraph(pkt.getData()[0]);
                        ecgChannelOne.updateGraph(pkt.getData()[1]);
                        ecgChannelTwo.updateGraph(pkt.getData()[2]);
                        ecgChannelTwo.updateGraph(pkt.getData()[3]);
                        ecgChannelThree.updateGraph(pkt.getData()[4]);
                        ecgChannelThree.updateGraph(pkt.getData()[5]);
                        Log.w(TAG, "Sequence Number: " + pkt.getPacketNumber());
                        mIndex++;
                        mHandler.postDelayed(mDisplayGraph, 1);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
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

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mHandler.removeCallbacks(mDisplayGraph);
    }
}
