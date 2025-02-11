package ro.cs.tao.functions;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

public class FolderResolver implements TokenResolver {

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        final String transformed = expression.endsWith("/")
                                   ? expression.substring(0, expression.length() - 1)
                                   : expression;
        final int idx = transformed.lastIndexOf('/');
        return idx > 0 ? transformed.substring(idx + 1) : transformed;
    }
}
