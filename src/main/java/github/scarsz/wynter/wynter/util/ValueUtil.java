package github.scarsz.wynter.wynter.util;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValueUtil {

    private static Pattern escapePattern = Pattern.compile("(?:[^,\\\\]+|\\\\.)+");

    public static Object parse(String value) {
        // booleans
        if (value.equalsIgnoreCase("yes")) return true;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("no")) return false;
        if (value.equalsIgnoreCase("false")) return false;

        // numbers
        if (StringUtils.isNumeric(value)) return Integer.parseInt(value);

        // arrays/strings
        List<String> matches = new LinkedList<>();
        Matcher matcher = escapePattern.matcher(value);
        while (matcher.find()) matches.add(matcher.group());
        if (matches.size() == 1) {
            return String.valueOf(value);
        } else {
            return matches.toArray();
        }
    }

}
