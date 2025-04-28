package ro.cs.tao.functions;

import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.utils.StringUtilities;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The FolderResolver class is an implementation of the TokenResolver interface
 * that resolves specific folder-related tokens embedded in a string expression.
 * This resolver supports patterns in the form of `FOLDER(nnXXX)` where `nn` is
 * a two-digit number and `XXX` is a sequence of three uppercase letters.
 * The matching token is replaced with the folder name derived from the second
 * part of the token (e.g., `nnXXX`).
 *
 * Responsibilities:
 * - Detect and resolve folder tokens in the given expressions using a predefined pattern.
 * - Transform any resolved tokens into a specific folder name based on the extracted information.
 *
 * Thread Safety:
 * This class is thread-safe since it does not maintain any mutable internal state.
 *
 * Regular Expression:
 * The folder pattern used for matching tokens is `FOLDER(nnXXX)` where:
 * - `nn` must be a two-digit number.
 * - `XXX` must be three uppercase alphabetical characters.
 *
 * Implementation Details:
 * - If the input string ends with a '/', the trailing '/' is removed.
 * - If the input contains no matching folder tokens, it is returned unmodified.
 * - If the input contains a matching folder token, it is replaced, and the modified
 *   string is returned.
 * - A static function, FOLDER, is used to extract the folder name by computing
 *   the substring after the last '/' in the input token.
 *
 * Error Handling:
 * - If the input expression is empty or null, it is returned unmodified.
 *
 * Usage Context:
 * This resolver is suitable for scenarios where folder-related tokens need to
 * be substituted or processed as part of string processing operations.
 *
 * Example Token Replacement:
 * Input: "path/to/FOLDER(12ABC)/file"
 * Output: "path/to/12ABC/file"
 */
public class FolderResolver implements TokenResolver {
    private static final Pattern folderPattern = Pattern.compile("(FOLDER\\((\\d{2}[A-Z]{3})\\))");

    public static final Function<String, String> FOLDER = (str) -> {
        final int idx = str.lastIndexOf('/');
        return idx > 0 ? str.substring(idx + 1) : str;
    };

    @Override
    public String resolve(String expression) {
        if (StringUtilities.isNullOrEmpty(expression)) {
            return expression;
        }
        String transformed = expression.endsWith("/")
                             ? expression.substring(0, expression.length() - 1)
                             : expression;
        Matcher matcher = folderPattern.matcher(expression);
        if (matcher.find()) {
            transformed = transformed.replace(matcher.group(1), FOLDER.apply(matcher.group(2)));
        }
        return transformed;
    }
}
