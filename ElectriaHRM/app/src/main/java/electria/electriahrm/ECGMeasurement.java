package electria.electriahrm;

/**
 * Created by augustio on 15.7.2015.
 */
public class ECGMeasurement {

    private String id;
    private String timeStamp;
    private String data;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

}
