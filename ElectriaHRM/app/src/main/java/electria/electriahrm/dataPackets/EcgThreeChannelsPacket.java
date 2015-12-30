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
            packetNumber = dataPacketArray[6];
            data = Arrays.copyOfRange(dataPacketArray, 0, dataPacketArray.length - 1);
        }
    }

    public int[] getData(){
        return data;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public long getPacketNumber(){
        return packetNumber;
    }
}
