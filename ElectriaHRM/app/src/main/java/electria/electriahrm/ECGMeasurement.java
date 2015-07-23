package electria.electriahrm;

import com.google.gson.Gson;


public class ECGMeasurement {

    private String sensor;
    private String timeStamp;
    private String data;

    public ECGMeasurement(){}

    public ECGMeasurement(String sensor, String timeStamp){
        this.sensor = sensor;
        this.timeStamp = timeStamp;
        data = "";
    }

    protected String getSensor() {
        return sensor;
    }

    protected void setSensor(String sensor) {
        this.sensor = sensor;
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

    protected String toJson(){

        Gson gson = new Gson();
        return gson.toJson(this);
    }

    protected void fromJson(String json){
        Gson gson = new Gson();
        ECGMeasurement ecgM = gson.fromJson(json, ECGMeasurement.class);
        this.sensor = ecgM.getSensor();
        this.timeStamp = ecgM.getTimeStamp();
        this.data = ecgM.getData();
    }

}
