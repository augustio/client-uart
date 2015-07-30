package electria.electriahrm.measurements;

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

    public String getSensor() {
        return sensor;
    }

    public void setSensor(String sensor) {
        this.sensor = sensor;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String toJson(){

        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public void fromJson(String json){
        Gson gson = new Gson();
        ECGMeasurement ecgM = gson.fromJson(json, ECGMeasurement.class);
        this.sensor = ecgM.getSensor();
        this.timeStamp = ecgM.getTimeStamp();
        this.data = ecgM.getData();
    }

}
