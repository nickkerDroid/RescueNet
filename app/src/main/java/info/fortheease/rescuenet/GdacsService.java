package info.fortheease.rescuenet;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import java.util.List;

public interface GdacsService {
    @GET("xml/gdacs.geojson")
    Call<ResponseBody> getTopDisasters();

    class GdacsResponse {
        public List<Feature> features;
    }

    class Feature {
        public Properties properties;
        public Geometry geometry;
    }

    class Properties {
        public String name;
        public String description;
        public String eventtype;
        public Object severity;
    }

    class Geometry {
        public String type;
        public Object coordinates; // Can be List<Double> or List<List<...>>
    }
}
