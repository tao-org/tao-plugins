package ro.cs.tao.datasource.remote.creodias.parsers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.remote.creodias.model.common.Token;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;

import java.util.logging.Logger;

public class LoginResponseHandler implements JSonResponseHandler<Token> {
    private final Logger logger = Logger.getLogger(LoginResponseHandler.class.getName());

    @Override
    public Token readValue(String content, AttributeFilter... filters) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(content, Token.class);
        } catch (Exception e) {
            logger.warning("Error parsing JSON: " + e.getMessage());
            return null;
        }
    }
}
