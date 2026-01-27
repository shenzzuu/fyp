package com.example.planprep;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddFoodActivity extends AppCompatActivity {

    private TextView tvMealTitle, tabAll, tabFavorites, tvEmptyState;
    private ImageView btnBack;
    private SearchView searchViewMeals;
    private RecyclerView rvFoodList;
    private FoodAdapter adapter;

    private FirebaseFirestore db;
    private String uid;
    private String mealType;
    private String dateId;
    private ProgressBar progressBar;

    // Filter Variables
    private String userLifestyle;
    private List<String> userAllergies = new ArrayList<>();
    private List<FoodItem> allFoodList = new ArrayList<>();
    private List<String> userFavoriteNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_food);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        mealType = getIntent().getStringExtra("MEAL_TYPE");
        dateId = getIntent().getStringExtra("DATE_ID");

        initViews();
        fetchUserPreferencesAndData();
        setupSearchLogic();
        setupTabLogic();
    }

    // 1. ADD: Handle Physical Back Button
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void initViews() {
        // --- 1. NEW HEADER LOGIC ---
        // Find the views from the included header layout
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);

        // IMPORTANT: Find the new back button ID
        btnBack = findViewById(R.id.btnHeaderBack);
        progressBar = findViewById(R.id.progressBar);

        // Setup the Title text
        if (mealType != null) {
            String capitalized = mealType.substring(0, 1).toUpperCase() + mealType.substring(1);
            tvHeaderTitle.setText("Add to " + capitalized);
        } else {
            tvHeaderTitle.setText("Add Meal");
        }

        // Hide subtitle (not needed) and ensure back button is visible
        tvHeaderSubtitle.setVisibility(View.GONE);
        btnBack.setVisibility(View.VISIBLE);

        // --- 2. HANDLE BACK BUTTON ---
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // --- 3. INITIALIZE REST OF THE VIEWS ---
        searchViewMeals = findViewById(R.id.searchViewMeals);
        rvFoodList = findViewById(R.id.rvFoodList);
        tabAll = findViewById(R.id.tabAll);
        tabFavorites = findViewById(R.id.tabFavorites);
        tvEmptyState = findViewById(R.id.tvEmptyState);
    }

    private void fetchUserPreferencesAndData() {
        progressBar.setVisibility(View.VISIBLE);
        if (uid == null) return;

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        userLifestyle = doc.getString("lifestyle");
                        List<String> allergies = (List<String>) doc.get("allergies");
                        if (allergies != null) userAllergies = allergies;
                    }
                    fetchFavoritesAndSetupData();
                })
                .addOnFailureListener(e -> fetchFavoritesAndSetupData());
    }

    private void fetchFavoritesAndSetupData() {
        db.collection("favorites")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userFavoriteNames.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString("mealName");
                        if (name != null) userFavoriteNames.add(name);
                    }
                    loadInitialData();
                })
                .addOnFailureListener(e -> loadInitialData());
    }

    private void loadInitialData() {
        progressBar.setVisibility(View.GONE);
        allFoodList = loadFoodFromJson(mealType);

        adapter = new FoodAdapter(this, allFoodList, foodItem -> {
            showTimePicker(foodItem);
        });

        rvFoodList.setLayoutManager(new GridLayoutManager(this, 2));
        rvFoodList.setAdapter(adapter);

        if (allFoodList.isEmpty()) {
            tvEmptyState.setText("No meals match your profile preferences.");
            tvEmptyState.setVisibility(View.VISIBLE);
        }
    }

    private List<FoodItem> loadFoodFromJson(String mealCategory) {
        List<FoodItem> items = new ArrayList<>();
        String jsonKey = (mealCategory != null) ? mealCategory.toLowerCase() : "breakfast";

        try {
            JSONObject ingredientsRoot = loadJsonFromAssets("ingredients.json");
            JSONObject categoryDetails = (ingredientsRoot != null) ? ingredientsRoot.optJSONObject(jsonKey) : null;
            JSONObject rootObject = loadJsonFromAssets("malaysian_food.json");

            if (rootObject != null && rootObject.has(jsonKey)) {
                JSONArray jsonArray = rootObject.getJSONArray(jsonKey);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String name = obj.getString("name");

                    List<String> mealAllergies = new ArrayList<>();
                    int ingredientCount = 0;

                    if (categoryDetails != null && categoryDetails.has(name)) {
                        JSONObject mealData = categoryDetails.getJSONObject(name);
                        JSONArray allergyArr = mealData.optJSONArray("allergies");
                        if (allergyArr != null) {
                            for (int j = 0; j < allergyArr.length(); j++) {
                                mealAllergies.add(allergyArr.getString(j));
                            }
                        }
                        JSONArray ingArr = mealData.optJSONArray("ingredients");
                        if (ingArr != null) ingredientCount = ingArr.length();
                    }

                    if (isSafeFromAllergies(mealAllergies) && matchesLifestyle(ingredientCount)) {
                        boolean isFav = userFavoriteNames.contains(name);
                        FoodItem item = new FoodItem(
                                name,
                                obj.getString("origin"),
                                obj.getString("image"),
                                isFav
                        );
                        item.setIngredients(fetchIngredientsList(jsonKey, name, ingredientsRoot));
                        items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        java.util.Collections.sort(items, (item1, item2) -> item1.getName().compareToIgnoreCase(item2.getName()));

        return items;
    }

    private JSONObject loadJsonFromAssets(String fileName) {
        try {
            InputStream is = getAssets().open(fileName);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) { return null; }
    }

    private boolean isSafeFromAllergies(List<String> mealAllergies) {
        if (userAllergies.isEmpty() || mealAllergies.isEmpty()) return true;
        for (String userAllergy : userAllergies) {
            for (String mealAllergy : mealAllergies) {
                if (mealAllergy.trim().equalsIgnoreCase(userAllergy.trim())) return false;
            }
        }
        return true;
    }

    private boolean matchesLifestyle(int count) {
        // Safety check
        if (userLifestyle == null) return true;

        switch (userLifestyle) {
            case "Always in a Rush":
                // Dropped to 6 to ensure these are actually "fast" meals
                return count <= 6;

            case "Moderately Busy":
                // 7 to 10 ingredients is a standard dinner
                return count > 6 && count <= 10;

            case "I Love Cooking":
                // 11+ ingredients allows for the complex meals (12, 15, 16)
                return count > 10;

            default:
                return true;
        }
    }

    private List<Ingredient> fetchIngredientsList(String category, String foodName, JSONObject root) {
        List<Ingredient> list = new ArrayList<>();
        try {
            if (root != null && root.has(category)) {
                JSONObject catObj = root.getJSONObject(category);
                if (catObj.has(foodName)) {
                    JSONArray ingArray = catObj.getJSONObject(foodName).getJSONArray("ingredients");
                    for (int i = 0; i < ingArray.length(); i++) {
                        list.add(new Ingredient(ingArray.getString(i), false, category, false));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    private void finalizeMealAddition(FoodItem foodItem, String time) {
        if (uid == null || mealType == null || dateId == null) return;

        String docId = uid + "_" + dateId;
        Map<String, Object> mealUpdates = new HashMap<>();
        mealUpdates.put("userId", uid);
        mealUpdates.put("date", dateId);
        mealUpdates.put(mealType, foodItem.getName());
        mealUpdates.put(mealType + "Time", time);
        mealUpdates.put(mealType + "Image", foodItem.getImageUrl());
        mealUpdates.put(mealType + "Eaten", false);
        mealUpdates.put(mealType + "Missed", false);

        Map<String, Object> ingredientMap = new HashMap<>();
        if (foodItem.getIngredients() != null) {
            for (Ingredient ing : foodItem.getIngredients()) {
                Map<String, Object> ingData = new HashMap<>();
                ingData.put("name", ing.getName());
                ingData.put("checked", false);
                ingData.put("category", mealType);
                ingData.put("isCustom", false);
                ingredientMap.put(ing.getFirestoreKey(), ingData);
            }
        }

        db.collection("meal_plans").document(docId).set(mealUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {

                    // --- NOTIFICATION UPDATE START ---
                    // dateId is usually "2025-12-01", time is "13:00"
                    scheduleNotificationForMeal(dateId, time, foodItem.getName());
                    // --- NOTIFICATION UPDATE END ---

                    Map<String, Object> groceryData = new HashMap<>();
                    groceryData.put("ingredients", ingredientMap);
                    groceryData.put("userId", uid);
                    groceryData.put("date", dateId);

                    db.collection("grocery_lists").document(docId).set(groceryData, SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> {
                                Toast.makeText(this, "Meal Added!", Toast.LENGTH_SHORT).show();
                                finish();
                                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                            });
                });
    }

    // COPY THIS HELPER METHOD INTO YOUR ACTIVITY CLASS
    private void scheduleNotificationForMeal(String dateStr, String timeStr, String mealName) {
        if (dateStr == null || timeStr == null || mealName == null) return;

        // Use a try-catch to ensure this NEVER crashes the app
        try {
            // Expected formats: dateStr="2025-11-28", timeStr="13:00" or "08:00"
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(dateStr + " " + timeStr);

            if (date != null) {
                int uniqueId = (mealName + date.getTime()).hashCode();

                // Call the safe scheduler
                com.example.planprep.NotificationScheduler.scheduleMealNotifications(
                        this,
                        uniqueId,
                        mealName,
                        date.getTime()
                );

                android.util.Log.d("MealPlan", "Alarm set for: " + mealName + " at " + date.toString());
            }
        } catch (Exception e) {
            // Log the error but DO NOT crash
            e.printStackTrace();
            android.util.Log.e("MealPlan", "Failed to schedule alarm: " + e.getMessage());
        }
    }

    private void setupTabLogic() {
        tabAll.setOnClickListener(v -> {
            updateTabStyles(true);
            if (adapter != null) adapter.filter("");
            tvEmptyState.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        });

        tabFavorites.setOnClickListener(v -> {
            updateTabStyles(false);
            if (adapter != null) {
                adapter.filterByTab(true, userFavoriteNames);
                tvEmptyState.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void updateTabStyles(boolean isAllSelected) {
        Context context = tabAll.getContext(); // Helper for colors

        if (isAllSelected) {
            // Active: All
            tabAll.setBackgroundResource(R.drawable.bg_green_rounded);
            tabAll.setTextColor(Color.WHITE);
            tabAll.setTypeface(null, Typeface.BOLD);

            // Inactive: Favorites
            tabFavorites.setBackground(null); // Transparent (shows container grey)
            tabFavorites.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            tabFavorites.setTypeface(null, Typeface.NORMAL);
        } else {
            // Active: Favorites
            tabFavorites.setBackgroundResource(R.drawable.bg_green_rounded);
            tabFavorites.setTextColor(Color.WHITE);
            tabFavorites.setTypeface(null, Typeface.BOLD);

            // Inactive: All
            tabAll.setBackground(null); // Transparent (shows container grey)
            tabAll.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            tabAll.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void setupSearchLogic() {
        searchViewMeals.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }
            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null) {
                    adapter.filter(newText);
                    tvEmptyState.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                }
                return true;
            }
        });
    }

    private void showTimePicker(FoodItem foodItem) {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(12)
                .setMinute(0)
                .setTitleText("Schedule " + foodItem.getName())
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            int h = timePicker.getHour();
            String amPm = (h >= 12) ? "PM" : "AM";
            int h12 = (h > 12) ? h - 12 : (h == 0 ? 12 : h);
            String formattedTime = String.format(Locale.getDefault(), "%02d:%02d %s", h12, timePicker.getMinute(), amPm);
            finalizeMealAddition(foodItem, formattedTime);
        });
        timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
    }
}