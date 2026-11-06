package ro.cs.tao.datasource.usgs.json.requests;

import tools.jackson.core.JacksonException;
import ro.cs.tao.serialization.JsonMapper;

public abstract class BaseRequest {

    @Override
    public String toString() {
        try {
            return JsonMapper.instance().writer().writeValueAsString(this);
        } catch (JacksonException e) {
            e.printStackTrace();
            return null;
        }
    }
}
