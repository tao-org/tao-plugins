package ro.cs.tao.datasource.usgs.json.requests;

import com.fasterxml.jackson.core.JsonProcessingException;
import ro.cs.tao.serialization.JsonMapper;

public abstract class BaseRequest {

    @Override
    public String toString() {
        try {
            return JsonMapper.instance().writer().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
