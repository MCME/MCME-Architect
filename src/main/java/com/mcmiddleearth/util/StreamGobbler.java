package com.mcmiddleearth.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class StreamGobbler implements Runnable {
    private final InputStream inputStream;
    private final InputStream errorStream;
    private final Consumer<String> consumer;

    public StreamGobbler(InputStream inputStream, InputStream errorStream, Consumer<String> consumer) {
        this.inputStream = inputStream;
        this.errorStream = errorStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(inputStream)).lines()
                .forEach(consumer);
        new BufferedReader(new InputStreamReader(errorStream)).lines()
                .forEach(consumer);
    }
}