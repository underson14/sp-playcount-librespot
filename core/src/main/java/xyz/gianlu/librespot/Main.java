package xyz.gianlu.librespot;

import org.apache.log4j.LogManager;
import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.mercury.MercuryClient;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * @author Gianlu
 */
public class Main {

    public static void main(String[] args) throws IOException, GeneralSecurityException, Session.SpotifyAuthenticationException, MercuryClient.MercuryException {
        AbsConfiguration conf = new FileConfiguration(args);
        LogManager.getRootLogger().setLevel(conf.loggingLevel());
        Session session = new Session.Builder(conf, args).create();

        HTTPServer server = new HTTPServer(conf, session.mercury());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                session.close();
                if (server.httpsServer != null) server.httpsServer.stop(1);
                if (server.httpServer != null) server.httpServer.stop(1);
                server.threadPoolExecutor.shutdownNow();
            } catch (IOException ignored) {
            }
        }));
    }
}


