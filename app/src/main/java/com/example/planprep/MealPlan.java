package com.example.planprep;

import java.util.HashMap;
import java.util.Map;

public class MealPlan {
    private String day;
    private String breakfast;
    private String lunch;
    private String dinner;
    private boolean isFavorite; // new

    public MealPlan() {}

    public MealPlan(String day, String breakfast, String lunch, String dinner) {
        this.day = day;
        this.breakfast = breakfast;
        this.lunch = lunch;
        this.dinner = dinner;
        this.isFavorite = false; // default
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("day", day);
        map.put("breakfast", breakfast);
        map.put("lunch", lunch);
        map.put("dinner", dinner);
        map.put("isFavorite", isFavorite);
        return map;
    }

    public boolean getIsFavorite() {
        return isFavorite;
    }

    public void setIsFavorite(boolean favorite) {
        this.isFavorite = favorite;
    }
}
