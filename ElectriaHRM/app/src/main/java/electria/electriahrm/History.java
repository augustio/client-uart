package electria.electriahrm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

import java.io.File;


public class History extends Activity {

    private static final String TAG = "ElectriaHRM";

    private ListView historyView;
    private ArrayAdapter<String> listAdapter;
    private String directoryName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyView = (ListView) findViewById(R.id.historyListView);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        historyView.setAdapter(listAdapter);
        historyView.setOnItemClickListener(mFileClickListener);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        directoryName = extras.getString(Intent.EXTRA_TEXT);
        readDirectory(directoryName);
    }

    private OnItemClickListener mFileClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id){
            String filePath = directoryName+"/"+listAdapter.getItem(position);
            Log.d(TAG, "File "+filePath+" selected");
        }
    };


    private void readDirectory(String dirName){
        if(isExternalStorageReadable()){
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File (root.getAbsolutePath() + dirName);
            if(!dir.exists())
                finish();
            for (File f : dir.listFiles()) {
                if (f.isFile())
                    listAdapter.add(f.getName());
                // do whatever you want with filename
            }
        }
        else {
            Log.w(TAG, "External storage not readable");
            finish();
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
}
