import com.flagstone.transform.DefineData;
import com.flagstone.transform.Movie;
import com.flagstone.transform.MovieTag;
import gamedata.furnidata.FurniData;
import gamedata.furnidata.furnidetails.FloorItemDetails;
import hotel.Hotel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.DataFormatException;

public class FurniDirections {
    public FurniDirections() throws IOException {
        List<FloorItemDetails> floorItems = new FurniData(Hotel.SANDBOX).getAllFloorItems();

        JSONArray directions = new JSONArray();
        Set<String> classNames = new HashSet<>();
        floorItems.parallelStream()
                .filter(item -> classNames.add(item.className.split("\\*")[0]))
                .forEach(item -> {
                    try {
                        Map<String, JSONObject> data = getBinaryDataFromSWF(String.format("https://images.habbo.com/dcr/hof_furni/%d/%s.swf", item.revision, item.className.split("\\*")[0]));
                        directions.put(getDirectionsFromBinaryData(data));
                    } catch (Exception ignored) {}
                });

        saveTextFile(new JSONObject().put("itemDirections",directions).toString(2), "directions.json");
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        new FurniDirections();
    }

    private Map<String, JSONObject> getBinaryDataFromSWF(String swfUrl) throws IOException, DataFormatException {
        Movie m = new Movie();
        Map<String, JSONObject> binaryData = new HashMap<>();

        m.decodeFromUrl(new URL(swfUrl));

        for (MovieTag mt : m.getObjects()) {
            if (mt instanceof DefineData) {
                DefineData data = (DefineData) mt;
                JSONObject xml = XML.toJSONObject(new String(data.getData()));
                binaryData.put(xml.keySet().stream().findFirst().get(), xml);
            }
        }

        return binaryData;
    }

    private JSONObject getDirectionsFromBinaryData(Map<String, JSONObject> data) {
        JSONObject visualizationData = data.get("visualizationData");
        JSONObject objectData = data.get("objectData");

        JSONArray directions = new JSONArray();

        try {
            Object directionAnglesObject = objectData
                    .getJSONObject("objectData")
                    .getJSONObject("model")
                    .getJSONObject("directions")
                    .get("direction");

            JSONArray directionAngles;

            if (directionAnglesObject instanceof JSONArray) {
                directionAngles = (JSONArray) directionAnglesObject;
            } else {
                directionAngles = new JSONArray();
                directionAngles.put(directionAnglesObject);
            }

            Object directionIdsObject = visualizationData
                    .getJSONObject("visualizationData")
                    .getJSONObject("graphics")
                    .getJSONArray("visualization")
                    .toList().stream()
                    .map(o -> (Map<String, Object>) o)
                    .map(JSONObject::new)
                    .filter(j -> j.getInt("size") == 64)
                    .findFirst().get()
                    .getJSONObject("directions")
                    .get("direction");

            JSONArray directionIds;
            if (directionIdsObject instanceof JSONArray) {
                directionIds = (JSONArray) directionIdsObject;
            } else {
                directionIds = new JSONArray();
                directionIds.put(directionIdsObject);
            }

            JSONObject dimensions = objectData
                    .getJSONObject("objectData")
                    .getJSONObject("model")
                    .getJSONObject("dimensions");

            for (int i = 0; i < directionAngles.length() && i < directionIds.length(); i++) {
                int angle = directionAngles.getJSONObject(i).getInt("id");
                int id = directionIds.getJSONObject(i).getInt("id");

                int x = angle % 180 == 0 ? dimensions.getInt("x") : dimensions.getInt("y");
                int y = angle % 180 == 0 ? dimensions.getInt("y") : dimensions.getInt("x");
                int z = dimensions.getInt("z");

                JSONObject dir = new JSONObject()
                        .put("id", id)
                        .put("angle", angle)
                        .put("x", x)
                        .put("y", y)
                        .put("z", z);

                directions.put(dir);
            }
        } catch (Exception ignored) {
            directions = new JSONArray();
        }

        return new JSONObject()
                .put("classname", objectData.getJSONObject("objectData").getString("type"))
                .put("directions", directions);
    }

    private void saveTextFile(String content, String fileName) {
        try {
            File dir = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getParentFile();
            System.out.println(content);
            FileWriter file = new FileWriter(new File(dir, fileName));
            file.write(content);
            file.flush();
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
