package com.example.planprep;

public class FavoriteItem {
    private String id;
    private String name;
    private String image;
    private String category; // <--- New Field

    public FavoriteItem() { }

    // Updated Constructor
    public FavoriteItem(String id, String name, String image, String category) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.category = category;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getImage() { return image; }
    public String getCategory() { return category; } // <--- New Getter
}