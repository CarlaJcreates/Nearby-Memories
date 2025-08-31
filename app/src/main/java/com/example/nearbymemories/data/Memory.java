package com.example.nearbymemories.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "memories")
public class Memory {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public double latitude;
    public double longitude;

    public String contactUri;     // nullable
    public String contactName;    // nullable
    public String mediaUri;       // nullable
    public String weatherSummary; // nullable
    public long createdAt;

    public Memory(String title, double latitude, double longitude,
                  String contactUri, String contactName, String mediaUri,
                  String weatherSummary, long createdAt) {
        this.title = title;
        this.latitude = latitude;
        this.longitude = longitude;
        this.contactUri = contactUri;
        this.contactName = contactName;
        this.mediaUri = mediaUri;
        this.weatherSummary = weatherSummary;
        this.createdAt = createdAt;
    }
}
