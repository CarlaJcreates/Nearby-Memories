package com.example.nearbymemories.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    LiveData<List<Memory>> getAll();

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    Memory getByIdSync(int id);

    @Insert
    long insert(Memory memory);

    @Update
    void update(Memory memory);
}
