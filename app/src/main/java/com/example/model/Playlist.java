package com.example.model;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    private final String id;
    private String name;
    private final List<String> trackIds;

    public Playlist(String id, String name, List<String> trackIds) {
        this.id = id;
        this.name = name;
        this.trackIds = trackIds != null ? new ArrayList<>(trackIds) : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getTrackIds() { return trackIds; }
}
