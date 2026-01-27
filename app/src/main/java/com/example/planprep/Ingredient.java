package com.example.planprep;

public class Ingredient {
    private String name;
    private boolean isChecked;
    private String category;
    private boolean isCustom; // Requirement: Distinguish added items from meal items

    // Updated Constructor
    public Ingredient(String name, boolean isChecked, String category, boolean isCustom) {
        this.name = name;
        this.isChecked = isChecked;
        this.category = category;
        this.isCustom = isCustom;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; } // Added for Editing capability

    public boolean isChecked() { return isChecked; }
    public void setChecked(boolean checked) { isChecked = checked; }

    public String getCategory() { return category; }

    public boolean isCustom() { return isCustom; } // Added to check for edit/delete permissions
    public void setCustom(boolean custom) { isCustom = custom; }

    /**
     * Helper to generate the Firestore field key.
     * Meal items: "Breakfast_Eggs"
     * Custom items: "CUSTOM_Breakfast_Extra Milk"
     */
    public String getFirestoreKey() {
        if (isCustom) {
            return "CUSTOM_" + category + "_" + name;
        } else {
            return category + "_" + name;
        }
    }
}