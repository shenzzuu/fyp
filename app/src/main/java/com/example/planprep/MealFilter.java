package com.example.planprep;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

public class MealFilter {
    public static boolean isMealMatch(Context context, List<String> mealAllergies, List<String> ingredients) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // 1. Check Allergies
        java.util.Set<String> userAllergies = prefs.getStringSet("allergies", new java.util.HashSet<>());
        if (mealAllergies != null) {
            for (String allergy : mealAllergies) {
                if (userAllergies.contains(allergy)) return false; // Hide if allergic
            }
        }

        // 2. Check Lifestyle (Ingredient Count)
        String lifestyle = prefs.getString("lifestyle", "");
        int count = (ingredients != null) ? ingredients.size() : 0;

        if ("Always in a Rush".equals(lifestyle)) return count <= 8;
        if ("Moderately Busy".equals(lifestyle)) return count > 8 && count <= 12;
        if ("I Love Cooking".equals(lifestyle)) return count > 12;

        return true;
    }
}