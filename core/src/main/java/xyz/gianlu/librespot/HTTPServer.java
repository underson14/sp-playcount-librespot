package xyz.gianlu.librespot;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.event.CacheEntryExpiredListener;
import xyz.gianlu.librespot.handler.ArtistAboutHandler;
import xyz.gianlu.librespot.handler.ArtistInfoHandler;
import xyz.gianlu.librespot.handler.PlayCountHandler;
import xyz.gianlu.librespot.mercury.MercuryClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HTTPServer {
    private AbsConfiguration conf;
    private MercuryClient mercuryClient;
    public HttpsServer httpsServer;
    public HttpServer httpServer;
    public ThreadPoolExecutor threadPoolExecutor;
    private Cache<String, String> cache;

    public HTTPServer(AbsConfiguration conf, MercuryClient mc) {
        this.conf = conf;
        this.mercuryClient = mc;
        this.cache = new Cache2kBuilder<String, String>() {}
                .name("cache")
                .entryCapacity(10000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .resilienceDuration(5, TimeUnit.SECONDS)
                .addListener((CacheEntryExpiredListener<String, String>) (cache, cacheEntry) -> {
                    cache.remove(cacheEntry.getKey()); // Remove cached result when it expires; will be refreshed when requested
                })
                .build();
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
                            ex.printStackTrace();
                        }
                    }
                });

                threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
                httpsServer.createContext(conf.albumEndpoint(), new PlayCountHandler(this.mercuryClient, this.cache));
                httpsServer.createContext(conf.artistEndpoint(), new ArtistInfoHandler(this.mercuryClient, this.cache));
                httpsServer.createContext(conf.artistAboutEndpoint(), new ArtistAboutHandler(this.mercuryClient, this.cache));
                httpsServer.setExecutor(threadPoolExecutor);
                httpsServer.start();
                System.out.println("Listening on port " + httpsServer.getAddress().getPort());
            } else {
                this.httpServer = HttpServer.create(address, 0);
                threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
                httpServer.createContext(conf.albumEndpoint(), new PlayCountHandler(this.mercuryClient, this.cache));
                httpServer.createContext(conf.artistEndpoint(), new ArtistInfoHandler(this.mercuryClient, this.cache));
                httpServer.createContext(conf.artistAboutEndpoint(), new ArtistAboutHandler(this.mercuryClient, this.cache));
                httpServer.setExecutor(threadPoolExecutor);
                httpServer.start();
                System.out.println("Listening on port " + httpServer.getAddress().getPort());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
