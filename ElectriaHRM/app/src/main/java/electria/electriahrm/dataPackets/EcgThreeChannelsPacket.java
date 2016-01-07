package electria.electriahrm.dataPackets;

import com.google.gson.Gson;

import java.util.Arrays;

/**
 * Created by augustio on 28.12.2015.
 */
public class EcgThreeChannelsPacket {

    private long packetNumber;
    private int dataId;
    private int[] data;

    public EcgThreeChannelsPacket(int[] dataPacketArray){
        if(dataPacketArray.length == 8) {
            packetNumber = dataPacketArray[1];
            dataId = dataPacketArray[0];
            data = Arrays.copyOfRange(dataPacketArray, 2, dataPacketArray.length);
        }
        else{
            packetNumber = 0;
            dataId = 0;
            data = null;
        }
    }

    public int[] getData(){
        return data;
    }

    public long getPacketNumber(){
        return packetNumber;
    }

    public int getDataId(){
        return dataId;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
