package xyz.gianlu.librespot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.*;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import xyz.gianlu.librespot.mercury.MercuryClient;
import xyz.gianlu.librespot.mercury.MercuryRequests;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class HTTPSServer {
    private AbsConfiguration conf;
    private MercuryClient mercuryClient;
    public HttpsServer httpsServer;
    public HttpServer httpServer;
    public ThreadPoolExecutor threadPoolExecutor;
    private Cache cache;
    private Gson gson;

    public HTTPSServer(AbsConfiguration conf, MercuryClient mc) throws IOException {
        this.conf = conf;
        this.mercuryClient = mc;
        CacheManager cm = CacheManager.getInstance();
        this.cache = cm.getCache("cache");
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    public class PlayCountHandler implements HttpHandler {
        private MercuryClient mercuryClient;
        private UrlParse urlParse;


        public PlayCountHandler(MercuryClient mc) {
            this.mercuryClient = mc;
            this.urlParse = new UrlParse();
        }

        @Override
        public void handle(HttpExchange httpEx) throws IOException {
            String response = "";
            int statusCode = 500;
            if (httpEx.getRequestMethod().equals("GET")) {
                Map<String, List<String>> query = urlParse.parse(httpEx.getRequestURI().getQuery());
                Map<String, Object> res = new HashMap<>();

                if (!query.containsKey("albumid")) {
                    statusCode = 400;
                    res.put("success", false);
                    res.put("data", "albumid is not defined in the query");
                } else if (query.get("albumid").get(0).length() != 22) {
                    statusCode = 400;
                    res.put("success", false);
                    res.put("data", "albumid is invalid; wanted 22 characters, found " + query.get("albumid").get(0).length());
                } else {
                    String albumId = query.get("albumid").get(0);
                    if (cache.isKeyInCache(albumId)) {
                        statusCode = 200;
                        response = cache.get(albumId).getObjectValue().toString();
                    } else {
                        try {
                            MercuryRequests.AlbumWrapper resp = this.mercuryClient.sendSync(MercuryRequests.getAlbumInfo(albumId)); // Get album info with albumId

//                            This part processes the output before sending, which results in lower response size. However, it doubles the response time.
//                            This could be fixed by optimizing it, but I don't feel like doing that right now :P

//                            Map<String, Object> data = new HashMap<>();
//                            data.put("name", resp.obj.get("name").getAsString());
//
//                            List<Map<String, Object>> discsArr = new ArrayList<>();
//                            JsonArray discs = resp.obj.getAsJsonArray("discs");
//                            for (JsonElement disc : discs) {
//                                Map<String, Object> discMap = new HashMap<>();
//                                discMap.put("number", disc.getAsJsonObject().get("number").getAsInt());
//                                discMap.put("name", disc.getAsJsonObject().get("name").getAsString());
//
//                                JsonArray tracks = disc.getAsJsonObject().getAsJsonArray("tracks");
//                                List<Map<String, Object>> tracksList = new ArrayList<>();
//                                for (JsonElement track : tracks) {
//                                    Map<String, Object> trackMap = new HashMap<>();
//                                    trackMap.put("name", track.getAsJsonObject().get("name").getAsString());
//                                    trackMap.put("playcount", track.getAsJsonObject().get("playcount").getAsInt());
//                                    trackMap.put("uri", track.getAsJsonObject().get("uri").getAsString());
//                                    tracksList.add(trackMap);
//                                }
//
//                                discMap.put("tracks", tracksList);
//                                discsArr.add(discMap);
//                            }
//
//                            data.put("discs", discsArr);

                            statusCode = 200;
                            res.put("success", true);
                            res.put("data", resp.obj);
                        } catch (Exception e) {
                            res.put("success", false);
                            if (e.getMessage().startsWith("status: ")) {
                                System.out.println(e.getMessage().substring(8));
                                statusCode = Integer.parseInt(e.getMessage().substring(8));
                                if (statusCode == 404) {
                                    res.put("data", "albumid invalid; couldn't find album");
                                } else {
                                    e.printStackTrace();
                                    res.put("data", "An unknown error has occurred; logged to console");
                                }
                            } else {
                                e.printStackTrace();
                                res.put("data", "An unknown error has occurred; logged to console");
                            }
                        }

                        response = gson.toJson(res);
                        if (statusCode == 200) { // If response was successful, save response in cache
                            cache.put(new Element(albumId, response));
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

    public void start() {
        try {
            InetSocketAddress address = new InetSocketAddress(conf.port());

            if (this.conf.enableHttps()) {
                this.httpsServer = HttpsServer.create(address, 0);
                SSLContext sslContext = SSLContext.getInstance("TLS");

                char[] password = Objects.requireNonNull(conf.httpsKsPass()).toCharArray();
                KeyStore ks = KeyStore.getInstance("JKS");
                FileInputStream fis = new FileInputStream(Objects.requireNonNull(conf.httpsKs()));
                ks.load(fis, password);

                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, password);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ks);

                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                    public void configure(HttpsParameters params) {
                        try {
                            SSLContext context = getSSLContext();
                            SSLEngine engine = context.createSSLEngine();
                            params.setNeedClientAuth(false);
                            params.setCipherSuites(engine.getEnabledCipherSuites());
                            params.setProtocols(engine.getEnabledProtocols());

                            SSLParameters sslParameters = context.getSupportedSSLParameters();
                            params.setSSLParameters(sslParameters);

                        } catch (Exception ex) {
                            System.out.println("Failed to create HTTPS port");
                        }
                    }
                });

                threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
                httpsServer.createContext(conf.endpoint(), new PlayCountHandler(this.mercuryClient));
                httpsServer.setExecutor(threadPoolExecutor);
                httpsServer.start();
            } else {
                this.httpServer = HttpServer.create(address, 0);
                threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
                httpServer.createContext(conf.endpoint(), new PlayCountHandler(this.mercuryClient));
                httpServer.setExecutor(threadPoolExecutor);
                httpServer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
