package com.example.planprep;

public class NotificationItem {
    private String id;
    private String title;
    private String body;
    private boolean read;

    public NotificationItem(String id, String title, String body, boolean read) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.read = read;
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isRead() { return read; }

    // Setter for read
    public void setRead(boolean read) {
        this.read = read;
    }
}