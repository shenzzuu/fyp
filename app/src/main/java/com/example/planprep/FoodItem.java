package com.example.planprep;

import java.util.ArrayList;
import java.util.List;

public class FoodItem {
    private String name;
    private String origin;
    private String imageUrl;
    private boolean isFavorite;
    private List<Ingredient> ingredients; // Added field

    public FoodItem(String name, String origin, String imageUrl, boolean isFavorite) {
        this.name = name;
        this.origin = origin;
        this.imageUrl = imageUrl;
        this.isFavorite = isFavorite;
        this.ingredients = new ArrayList<>();
    }

    public String getName() { return name; }
    public String getOrigin() { return origin; }
    public String getImageUrl() { return imageUrl; }
    public boolean isFavorite() { return isFavorite; }

    // Getters and Setters for ingredients
    public List<Ingredient> getIngredients() { return ingredients; }
    public void setIngredients(List<Ingredient> ingredients) { this.ingredients = ingredients; }
}