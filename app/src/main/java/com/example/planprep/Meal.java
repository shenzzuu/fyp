package com.example.planprep;

public class Meal {
    private String name;
    private String description;
    private String category;
    private String imageUrl;

    private String scheduledDate; // e.g., "Mon, 12 Oct"
    private String scheduledTime;

    // 🔹 Empty constructor (required for Firebase deserialization)
    public Meal() {}

    // 🔹 Constructor for Firebase or manual creation
    public Meal(String name, String description, String category, String imageUrl) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.imageUrl = imageUrl;
    }

    // 🔹 Overloaded constructor for API usage (no category needed)
    public Meal(String name, String description, String imageUrl) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = "Uncategorized"; // fallback for API data
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public String getScheduledDate() { return scheduledDate; }
    public String getScheduledTime() { return scheduledTime; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }
}