package electria.electriahrm;

import com.google.gson.Gson;


public class ECGMeasurement {

    private String id;
    private String timeStamp;
    private String data;

    public void ECGMeasurement(String id, String timeStamp){
        this.id = id;
        this.timeStamp = timeStamp;
    }

    protected String getId() {
        return id;
    }

    protected void setId(String id) {
        this.id = id;
    }

    protected String getTimeStamp() {
        return timeStamp;
    }

    protected void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    protected String getData() {
        return data;
    }

    protected void setData(String data) {
        this.data = data;
    }

    protected void addValue(String value){
        data = value + "\n";
    }

    protected String toJson(){

        Gson gson = new Gson();
        return gson.toJson(this);
    }

    protected void fromJson(String json){
        Gson gson = new Gson();
        ECGMeasurement ecgM = gson.fromJson(json, ECGMeasurement.class);
        this.id = ecgM.getId();
        this.timeStamp = ecgM.getTimeStamp();
        this.data = ecgM.getData();
    }

}
