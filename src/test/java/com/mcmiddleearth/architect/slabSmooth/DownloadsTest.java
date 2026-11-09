package com.mcmiddleearth.architect.slabSmooth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Downloads from a server on this machine only (127.0.0.1, a free port), so nothing goes to the internet. */
class DownloadsTest {

    @TempDir
    Path dir;

    // A server that sends its headers and then stops sending would hold the packs' worker, and with it every pack's
    // builds and change checks, until a reload. So the whole download, the body too, has a time limit.
    @Test
    void aDownloadThatStallsFailsAtItsLimitAndLeavesNoPartialFile() throws Exception {
        CountDownLatch stop = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/stalls.zip", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 1_000_000);
                OutputStream body = exchange.getResponseBody();
                body.write(new byte[1000]); // a start, and then nothing more
                body.flush();
                stop.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        Downloads downloads = new Downloads(Logger.getAnonymousLogger());
        Path target = dir.resolve("cache").resolve("pack.zip");
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stalls.zip";
            long start = System.nanoTime();

            IOException failed = assertThrows(IOException.class, () -> downloads.fetch(url, target, "", Duration.ofMillis(500)));

            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(ms < 10_000, "it gave up at its limit, not when the server did: " + ms + " ms");
            assertTrue(failed.getMessage().contains("took longer than"), failed.getMessage());
            assertFalse(Files.exists(target), "nothing was downloaded");
            assertFalse(Files.exists(target.resolveSibling("pack.zip.part")), "and no partial file is left");
        } finally {
            stop.countDown();
            downloads.close();
            server.stop(0);
        }
    }
}
