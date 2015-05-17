package electria.electriahrm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

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
        historyView.setDivider(null);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        directoryName = extras.getString(Intent.EXTRA_TEXT);
    }

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
        else
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
