package pontezit.android.tilos.com.api;

import com.loopj.android.http.*;

import pontezit.android.tilos.com.utils.Finals;

public class TilosRestClient {


    private static AsyncHttpClient client = new AsyncHttpClient();

    public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.get(getAbsoluteUrl(url), params, responseHandler);
    }

    public static void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.post(getAbsoluteUrl(url), params, responseHandler);
    }

    private static String getAbsoluteUrl(String relativeUrl) {
        return Finals.API_BASE_URL + relativeUrl;
    }
}
