package com.example.planprep;

import com.google.firebase.Timestamp;

public class NotificationModel {
    private String docId;
    private String title;
    private String message;
    private String type;
    private boolean read;
    private Timestamp timestamp;

    public NotificationModel() {} // Required for Firestore

    public NotificationModel(String title, String message, String type, boolean read, Timestamp timestamp) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.read = read;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public boolean isRead() { return read; }
    public Timestamp getTimestamp() { return timestamp; }

    public void setRead(boolean read) { this.read = read; }
}