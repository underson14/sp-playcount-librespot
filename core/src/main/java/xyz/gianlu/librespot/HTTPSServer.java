package xyz.gianlu.librespot;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import xyz.gianlu.librespot.mercury.MercuryClient;
import xyz.gianlu.librespot.mercury.MercuryRequests;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class HTTPSServer {
    private AbsConfiguration conf;
    private MercuryClient mercuryClient;
    public HttpsServer httpsServer;
    public HttpServer httpServer;
    public ThreadPoolExecutor threadPoolExecutor;
    private Cache cache;

    public HTTPSServer(AbsConfiguration conf, MercuryClient mc) throws IOException {
        this.conf = conf;
        this.mercuryClient = mc;
        CacheManager cm = CacheManager.getInstance();
        this.cache = cm.getCache("cache");
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
                        if (cache.isKeyInCache(albumId)) {
                            System.out.println("Retrieving from cache: " + albumId);
                            statusCode = 200;
                            response = cache.get(albumId).getObjectValue().toString();
                        } else {
                            try {
                                MercuryRequests.AlbumWrapper resp = this.mercuryClient.sendSync(MercuryRequests.getAlbumInfo(albumId)); // Get album info with albumId

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
                            if (statusCode == 200 && !cache.isKeyInCache(albumId)) { // If response was successful, save response in cache
                                System.out.println("Adding to cache: " + albumId + " (Cache size: " + cache.getSize() + ")");
                                cache.put(new Element(albumId, response));
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

    public void start() {
        try {
            InetSocketAddress address = new InetSocketAddress(System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : conf.port());

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
                System.out.println("Listening on port " + httpsServer.getAddress().getPort());
            } else {
                this.httpServer = HttpServer.create(address, 0);
                threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
                httpServer.createContext(conf.endpoint(), new PlayCountHandler(this.mercuryClient));
                httpServer.setExecutor(threadPoolExecutor);
                httpServer.start();
                System.out.println("Listening on port " + httpServer.getAddress().getPort());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
