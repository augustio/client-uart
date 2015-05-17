package electria.electriahrm;

import android.app.Activity;;
import android.os.Bundle;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;


public class HistoryDetail extends Activity {

    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ViewGroup historyViewLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = LineGraphView.getLineGraphView();
        mGraphView = mLineGraph.getView(this);
        historyViewLayout = (ViewGroup) findViewById(R.id.history_detail);
        historyViewLayout.addView(mGraphView);
    }

}
