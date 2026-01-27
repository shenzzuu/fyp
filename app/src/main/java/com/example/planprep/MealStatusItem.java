package com.example.planprep;

import java.util.Date;

public class MealStatusItem implements Comparable<MealStatusItem> {
    String mealName;
    String mealType;
    String dateStr;     // Original ID format "yyyy-MM-dd"
    Date dateObj;       // For sorting
    boolean isEaten;

    public MealStatusItem(String mealName, String mealType, String dateStr, Date dateObj, boolean isEaten) {
        this.mealName = mealName;
        this.mealType = mealType;
        this.dateStr = dateStr;
        this.dateObj = dateObj;
        this.isEaten = isEaten;
    }

    @Override
    public int compareTo(MealStatusItem o) {
        // Sort by date descending (newest first)
        return o.dateObj.compareTo(this.dateObj);
    }
}