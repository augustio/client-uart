package electria.electriahrm.dataPackets;

import com.google.gson.Gson;

import java.util.Arrays;

/**
 * Created by augustio on 28.12.2015.
 */
public class EcgThreeChannelsPacket {

    private long packetNumber;
    private int[] data;

    public EcgThreeChannelsPacket(int[] dataPacketArray){
        if(dataPacketArray.length == 7) {
            packetNumber = dataPacketArray[0];
            data = Arrays.copyOfRange(dataPacketArray, 1, dataPacketArray.length);
        }
    }

    public int[] getData(){
        return data;
    }

    public void setData(int[] data){
        this.data = Arrays.copyOf(data, data.length);
    }

    public String toJason(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
