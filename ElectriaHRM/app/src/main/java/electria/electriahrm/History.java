package electria.electriahrm;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;


public class History extends Activity {

    private ListView historyView;
    private ArrayAdapter<String> listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyView = (ListView) findViewById(R.id.historyListView);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        historyView.setAdapter(listAdapter);
        historyView.setDivider(null);
    }
}
