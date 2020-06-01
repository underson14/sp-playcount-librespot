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

// This class may get merged with PlayCountHandler, as most of it is basically the same.
public class ArtistInsightsHandler implements HttpHandler {
    private MercuryClient mercuryClient;
    private UrlParse urlParse;
    private Cache<String, String> cache;

    public ArtistInsightsHandler(MercuryClient mc, Cache<String, String> cache) {
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
                res.put("data", "artistid is not defined in the query");
                response = String.format("{\"success\": %s, \"data\": %s}", res.get("success"), res.get("data"));
            } else {
                Map<String, List<String>> query = urlParse.parse(httpEx.getRequestURI().getQuery());
                if (!query.containsKey("artistid")) {
                    statusCode = 400;
                    res.put("success", false);
                    res.put("data", "artistid is not defined in the query");
                    response = String.format("{\"success\": %s, \"data\": %s}", res.get("success"), res.get("data"));
                } else if (query.get("artistid").get(0).length() != 22) {
                    statusCode = 400;
                    res.put("success", false);
                    res.put("data", "artistid is invalid; artistid length does not equal 22");
                    response = String.format("{\"success\": %s, \"data\": %s}", res.get("success"), res.get("data"));
                } else {
                    String artistId = query.get("artistid").get(0);
                    if (cache.containsKey("artist_insights:" + artistId)) {
                        statusCode = 200;
                        response = cache.get("artist_insights:" + artistId);
                    } else {
                        try {
                            MercuryRequests.GenericJsonWrapper resp = this.mercuryClient.sendSync(MercuryRequests.getArtistInsights(artistId)); // Get artist insights with artistId
                            System.out.println(resp.obj);

                            statusCode = 200;
                            res.put("success", true);
                            res.put("data", resp.obj);
                        } catch (Exception e) {
                            res.put("success", false);
                            if (e.getMessage().startsWith("status: ")) {
                                statusCode = Integer.parseInt(e.getMessage().substring(8));
                                if (statusCode == 404) {
                                    res.put("data", "artistid invalid; couldn't find artist");
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
                            cache.putIfAbsent("artist_insights:" + artistId, response);
                        }
                    }
                }

            }
        } else { // Invalid request type
            statusCode = 404;
            response = "Cannot " + httpEx.getRequestMethod() + " " + httpEx.getRequestURI().toString();
        }

        httpEx.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        httpEx.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = httpEx.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
