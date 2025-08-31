package com.example.nearbymemories.util;

import android.content.Context;
import androidx.room.Room;
import com.example.nearbymemories.data.AppDatabase;

public class Db {
    public static AppDatabase instance;

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "memories.db"
            ).build();
        }
    }
}
