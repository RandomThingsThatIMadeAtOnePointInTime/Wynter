package github.scarsz.wynter.wynter.util;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

public class HttpUtil {

    public static String requestHttp(String requestUrl) {
        try {
            return IOUtils.toString(new URL(requestUrl), Charset.defaultCharset());
        } catch (IOException e) {
            System.out.println("Error: failed to request URL " + requestUrl + ": " + e.getMessage());
            return "";
        }
    }

}
