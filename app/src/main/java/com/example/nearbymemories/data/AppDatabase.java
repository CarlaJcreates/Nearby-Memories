package com.example.nearbymemories.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Memory.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MemoryDao memoryDao();
}
