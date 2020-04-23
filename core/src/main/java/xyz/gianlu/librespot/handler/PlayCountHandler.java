package xyz.gianlu.librespot.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.cache2k.Cache;
import xyz.gianlu.librespot.UrlParse;
import xyz.gianlu.librespot.mercury.MercuryClient;
import xyz.gianlu.librespot.mercury.MercuryRequests;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayCountHandler implements HttpHandler {
    private MercuryClient mercuryClient;
    private UrlParse urlParse;
    private Cache<String, String> cache;

    public PlayCountHandler(MercuryClient mc, Cache<String, String> cache) {
        this.mercuryClient = mc;
        this.urlParse = new UrlParse();
        this.cache = cache;
    }

    @Override
    public void handle(HttpExchange httpEx) throws IOException {
        String response;
        int statusCode;
        if (httpEx.getRequestMethod().equals("GET")) {
            Map<String, Object> res = new HashMap<>();
            if (httpEx.getRequestURI().getQuery() == null) {
                statusCode = 400;
                res.put("success", false);
                res.put("data", "albumid is not defined in the query");
                response = String.format("{\"success\": %s, \"data\": %s}", res.get("success"), res.get("data"));
            } else {
                Map<String, List<String>> query = urlParse.parse(httpEx.getRequestURI().getQuery());
                if (!query.containsKey("albumid")) {
                    statusCode = 400;
                    res.put("success", false);
                    res.put("data", "albumid is not defined in the query");
                    response = String.format("{\"success\": %s, \"data\": %s}", res.get("success"), res.get("data")); // TODO: don't repeat this line ffs
                } else if (query.get("albumid").get(0).length() != 22) {
                    statusCode = 400;
                    res.put("success", false);
                    res.put("data", "albumid is invalid; albumid length does not equal 22");
                    response = String.format("{\"success\": %s, \"data\": %s}", res.get("success"), res.get("data"));
                } else {
                    String albumId = query.get("albumid").get(0);
                    if (cache.containsKey("album:" + albumId)) {
                        statusCode = 200;
                        response = cache.get("album:" + albumId);
                    } else {
                        try {
                            MercuryRequests.GenericJsonWrapper resp = this.mercuryClient.sendSync(MercuryRequests.getAlbumInfo(albumId)); // Get album info with albumId

                            statusCode = 200;
                            res.put("success", true);
                            res.put("data", resp.obj);
                        } catch (Exception e) {
                            res.put("success", false);
                            if (e.getMessage().startsWith("status: ")) {
                                statusCode = Integer.parseInt(e.getMessage().substring(8));
                                if (statusCode == 404) {
                                    res.put("data", "albumid invalid; couldn't find album");
                                } else {
                                    e.printStackTrace();
                                    res.put("data", "An unknown error has occurred; logged to console");
                                }
                            } else {
                                statusCode = 500;
                                e.printStackTrace();
                                res.put("data", "An unknown error has occurred; logged to console");
                            }
                        }

                        response = String.format("{\"success\": %s, \"data\": %s}", res.get("success"), res.get("data"));
                        if (statusCode == 200) { // If response was successful, save response in cache
                            cache.putIfAbsent("album:" + albumId, response);
                        }
                    }
                }

            }
        } else { // Invalid request type
            statusCode = 404;
            response = "Cannot " + httpEx.getRequestMethod() + " " + httpEx.getRequestURI().toString();
        }

        httpEx.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        httpEx.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        httpEx.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = httpEx.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
