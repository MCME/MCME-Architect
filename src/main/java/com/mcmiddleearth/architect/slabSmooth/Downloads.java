package com.mcmiddleearth.architect.slabSmooth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/** Fetches files into SlabSmooth's cache folder, checked against their SHA-1 where one is known. Off the main thread. */
final class Downloads {

    /** Packs are ~20-50 MB and the client jar ~30 MB; anything far bigger is a wrong URL. */
    static final long MAX_BYTES = 512L * 1024 * 1024;
    /** The longest one download may take, from asking to the last byte of the body. */
    static final Duration TIME_LIMIT = Duration.ofMinutes(10);
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    /** Made on the first download (its selector thread runs from then on), so a server that never downloads has none. */
    private HttpClient http;
    private final Logger log;

    Downloads(Logger log) {
        this.log = log;
    }

    private synchronized HttpClient http() {
        if (http == null) {
            // NORMAL: follows redirects, but never from https to http.
            http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
        }
        return http;
    }

    /** Stops the client and any download in progress. */
    synchronized void close() {
        if (http != null) {
            http.shutdownNow();
            http = null;
        }
    }

    /** Downloads url to target (replacing it only once the download is complete and checked), within TIME_LIMIT. */
    void fetch(String url, Path target, String sha1) throws IOException {
        fetch(url, target, sha1, TIME_LIMIT);
    }

    /**
     * As above, within limit: the answer and the whole body. The request's own timeout ends with the answer's headers,
     * and a server that then stops sending would block the read, and with it the packs' worker, for ever. At the limit
     * the body is closed, which ends the read: the download fails like any other, and its partial file is deleted.
     */
    void fetch(String url, Path target, String sha1, Duration limit) throws IOException {
        long deadline = System.nanoTime() + limit.toNanos();
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(limit) // until the answer's headers
                .header("User-Agent", "MCME-Architect (Minecraft server plugin)").GET().build();
        HttpResponse<InputStream> response;
        try {
            response = http().send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        MessageDigest digest = sha1();
        long total = 0;
        InputStream body = response.body();
        AtomicReference<InputStream> watched = new AtomicReference<>(body); // cleared when the read has ended
        AtomicBoolean late = new AtomicBoolean();
        CompletableFuture<Void> watch = CompletableFuture.runAsync(() -> {
            InputStream unfinished = watched.getAndSet(null);
            if (unfinished != null) {
                late.set(true);
                closeQuietly(unfinished);
            }
        }, CompletableFuture.delayedExecutor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
        try (InputStream in = body; OutputStream out = Files.newOutputStream(part)) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new IOException("more than " + MAX_BYTES / 1048576 + " MB: " + url);
                }
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw late.get() ? new IOException("the download took longer than " + describe(limit) + ": " + url, e) : e;
        } finally {
            watched.set(null);
            watch.cancel(false);
        }
        String got = HexFormat.of().formatHex(digest.digest());
        if (sha1 != null && !sha1.isEmpty() && !sha1.equalsIgnoreCase(got)) {
            Files.deleteIfExists(part);
            throw new IOException("checksum mismatch for " + url + ": expected " + sha1 + ", got " + got);
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info("Downloaded " + url + " (" + total / 1048576 + " MB).");
    }

    /**
     * The vanilla client jar of a Minecraft version (for the block models packs build on), from Mojang's version
     * manifest, kept in cacheDir. Downloaded once per version.
     */
    Path clientJar(String version, Path cacheDir) throws IOException {
        Path jar = cacheDir.resolve("client-" + version + ".jar");
        Path shaFile = cacheDir.resolve("client-" + version + ".sha1");
        if (Files.isRegularFile(jar) && Files.isRegularFile(shaFile)
                && Files.readString(shaFile).trim().equalsIgnoreCase(sha1Of(jar))) {
            return jar;
        }
        JsonObject manifest = json(MANIFEST);
        String versionUrl = null;
        for (JsonElement v : manifest.getAsJsonArray("versions")) {
            if (v.getAsJsonObject().get("id").getAsString().equals(version)) {
                versionUrl = v.getAsJsonObject().get("url").getAsString();
            }
        }
        if (versionUrl == null) {
            throw new IOException("Minecraft " + version + " isn't in Mojang's version manifest; set slabSmooth.vanilla.clientJar in config.yml");
        }
        JsonObject client = json(versionUrl).getAsJsonObject("downloads").getAsJsonObject("client");
        String sha = client.get("sha1").getAsString();
        fetch(client.get("url").getAsString(), jar, sha);
        Files.writeString(shaFile, sha);
        return jar;
    }

    private JsonObject json(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
        try {
            HttpResponse<String> r = http().send(request, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IOException("HTTP " + r.statusCode() + " for " + url);
            }
            return JsonParser.parseString(r.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // the read it ends fails anyway, and says why
        }
    }

    private static String describe(Duration limit) {
        return limit.toMillis() % 60_000 == 0 ? limit.toMinutes() + " minutes" : limit.toMillis() + " ms";
    }

    static String sha1Of(Path file) throws IOException {
        MessageDigest digest = sha1();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
