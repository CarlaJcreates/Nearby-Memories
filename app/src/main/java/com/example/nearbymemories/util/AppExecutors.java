package com.example.nearbymemories.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutors {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
}
