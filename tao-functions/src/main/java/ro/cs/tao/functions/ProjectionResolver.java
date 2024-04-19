package ro.cs.tao.functions;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectionResolver implements TokenResolver {
    private static final Pattern utmZonePattern = Pattern.compile("(EPSG_CODE\\((\\d{2}[A-Z]{3})\\))");

    public static final Function<String, String> EPSG_CODE = (str) -> {
        final int code = Integer.parseInt(str.substring(0, 2));
        return "EPSG:" + (str.charAt(0) < 70 ? 32600 + code : 32700 + code);
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression;
        try {
            Matcher matcher = utmZonePattern.matcher(expression);
            if (matcher.find()) {
                transformed = transformed.replace(matcher.group(1), EPSG_CODE.apply(matcher.group(2)));
            }
        } catch (Throwable t) {
            Logger.getLogger(ProjectionResolver.class.getName()).warning(t.getMessage());
        }
        return transformed;
    }
}
