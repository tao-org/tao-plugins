package ro.cs.tao.datasource.remote.das.json;

import ro.cs.tao.datasource.remote.das.common.Token;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.serialization.JsonMapper;

import java.util.logging.Logger;

public class LoginResponseHandler implements JSonResponseHandler<Token> {
    private final Logger logger = Logger.getLogger(LoginResponseHandler.class.getName());

    @Override
    public Token readValue(String content, AttributeFilter... filters) {
        try {
            return JsonMapper.instance().readValue(content, Token.class);
        } catch (Exception e) {
            logger.warning("Error parsing JSON: " + e.getMessage());
            return null;
        }
    }
}
