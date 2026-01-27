package com.example.planprep;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MealListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MealAdapter adapter;
    private SearchView searchView;
    private View btnBack;
    private TextView tvMealsTitle;

    private List<Meal> mealList = new ArrayList<>();
    private FirebaseFirestore db;
    private String uid;
    private String currentCategory;

    private String userLifestyle;
    private List<String> userAllergies = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_list);

        // --- 1. NEW HEADER INIT ---
        // We now look for views inside the included header layout
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        ImageView btnHeaderBack = findViewById(R.id.btnHeaderBack);

        // Initialize the other views
        recyclerView = findViewById(R.id.recyclerViewMeals);
        searchView = findViewById(R.id.searchViewMeals);

        // --- 2. CONFIGURE HEADER ---
        tvHeaderSubtitle.setVisibility(View.GONE); // Hide subtitle for this screen
        btnHeaderBack.setVisibility(View.VISIBLE); // Ensure back button is visible

        // Handle Back Button Click
        btnHeaderBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // --- 3. REST OF LOGIC ---
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        currentCategory = getIntent().getStringExtra("category");
        String mealsJson = getIntent().getStringExtra("meals_json");

        // Set the Title on the Custom Header
        if (currentCategory != null) {
            String formattedTitle = currentCategory.substring(0, 1).toUpperCase() + currentCategory.substring(1).toLowerCase();
            tvHeaderTitle.setText(formattedTitle);
        } else {
            tvHeaderTitle.setText("Meals");
        }

        fetchUserPreferencesAndLoad(mealsJson);
        initSearchFilter();
    }

    // 2. ADD: Handle physical back button (Android system back button)
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void fetchUserPreferencesAndLoad(String mealsJson) {
        if (uid == null) return;
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        userLifestyle = doc.getString("lifestyle");
                        userAllergies = (List<String>) doc.get("allergies");
                        if (userAllergies == null) userAllergies = new ArrayList<>();
                    }
                    fetchUserFavorites();
                    loadAndFilterMeals(mealsJson);
                });
    }

    private void loadAndFilterMeals(String mealsJson) {
        try {
            JSONObject ingredientsRoot = loadIngredientsJSON();
            if (ingredientsRoot == null) return;

            Gson gson = new Gson();
            List<Map<String, String>> inputMeals = gson.fromJson(
                    mealsJson, new TypeToken<List<Map<String, String>>>() {}.getType()
            );

            mealList.clear();
            JSONObject categoryData = ingredientsRoot.optJSONObject(currentCategory.toLowerCase());

            for (Map<String, String> m : inputMeals) {
                String mealName = m.get("name");
                List<String> mealAllergies = new ArrayList<>();
                int ingredientCount = 0;

                if (categoryData != null && categoryData.has(mealName)) {
                    JSONObject mealDetails = categoryData.getJSONObject(mealName);
                    JSONArray allergyArray = mealDetails.optJSONArray("allergies");
                    if (allergyArray != null) {
                        for (int i = 0; i < allergyArray.length(); i++) {
                            mealAllergies.add(allergyArray.getString(i));
                        }
                    }
                    JSONArray ingArray = mealDetails.optJSONArray("ingredients");
                    if (ingArray != null) {
                        ingredientCount = ingArray.length();
                    }
                }

                if (isSafe(mealAllergies) && matchesLifestyle(ingredientCount)) {
                    mealList.add(new Meal(
                            mealName,
                            "Delicious " + currentCategory,
                            currentCategory,
                            m.get("image")
                    ));
                }
            }

            java.util.Collections.sort(mealList, (m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));

            adapter = new MealAdapter(mealList);
            adapter.setOnMealActionListener(this::showDatePicker);
            adapter.setOnFavoriteClickListener(this::toggleFavorite);
            recyclerView.setAdapter(adapter);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JSONObject loadIngredientsJSON() {
        try {
            InputStream is = getAssets().open("ingredients.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSafe(List<String> mealAllergies) {
        if (userAllergies.isEmpty()) return true;
        for (String uA : userAllergies) {
            for (String mA : mealAllergies) {
                if (mA.trim().equalsIgnoreCase(uA.trim())) return false;
            }
        }
        return true;
    }

    private boolean matchesLifestyle(int count) {
        if (userLifestyle == null) return true;

        if (userLifestyle.equals("Always in a Rush")) {
            // Updated to <= 6 to capture meals: 3, 4, 5, 6 (4 items)
            return count <= 6;
        } else if (userLifestyle.equals("Moderately Busy")) {
            // Updated to 7-10 to capture meals: 7, 8, 9, 10 (4 items)
            return count > 6 && count <= 10;
        } else if (userLifestyle.equals("I Love Cooking")) {
            // Updated to > 10 to capture meals: 12, 15, 16 (3 items)
            return count > 10;
        }
        return true;
    }

    private void fetchUserFavorites() {
        db.collection("favorites").whereEqualTo("userId", uid).get()
                .addOnSuccessListener(snapshots -> {
                    List<String> favNames = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots) favNames.add(doc.getString("mealName"));
                    if (adapter != null) adapter.setFavoriteMeals(favNames);
                });
    }

    private void toggleFavorite(Meal meal, boolean isCurrentlyFav) {
        if (isCurrentlyFav) {
            // --- REMOVE FROM FAVORITES ---
            db.collection("favorites")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("mealName", meal.getName())
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (DocumentSnapshot d : queryDocumentSnapshots) {
                            d.getReference().delete();
                        }
                        fetchUserFavorites(); // Refresh local list
                    });
        } else {
            // --- ADD TO FAVORITES (Updated to match your screenshot) ---
            Map<String, Object> data = new HashMap<>();

            // 1. Core ID fields
            data.put("userId", uid);
            data.put("mealName", meal.getName());

            // 2. Extra Details (Make sure your Meal class has these getters)
            data.put("category", meal.getCategory());       // e.g. "dinner"
            data.put("description", meal.getDescription()); // e.g. "Popular Malaysian..."
            data.put("imageUrl", meal.getImageUrl());       // e.g. "https://..."

            // 3. Timestamp
            data.put("timestamp", new java.util.Date());    // Saves current time

            db.collection("favorites")
                    .add(data)
                    .addOnSuccessListener(documentReference -> {
                        fetchUserFavorites(); // Refresh local list
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("FavError", "Error adding favorite", e);
                    });
        }
    }

    private void initSearchFilter() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String n) {
                if (adapter != null) adapter.filter(n);
                return true;
            }
        });
    }

    private void showDatePicker(Meal meal) {
        MaterialDatePicker<Long> dp = MaterialDatePicker.Builder.datePicker().build();
        dp.addOnPositiveButtonClickListener(s -> {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(s);
            String dId = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
            meal.setScheduledDate(dId);
            showTimePicker(meal);
        });
        dp.show(getSupportFragmentManager(), "DP");
    }

    private void showTimePicker(Meal meal) {
        MaterialTimePicker tp = new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).build();
        tp.addOnPositiveButtonClickListener(v -> {
            meal.setScheduledTime(String.format(Locale.getDefault(), "%02d:%02d", tp.getHour(), tp.getMinute()));
            saveToFirebase(meal);
        });
        tp.show(getSupportFragmentManager(), "TP");
    }

    private void saveToFirebase(Meal meal) {
        String docId = uid + "_" + meal.getScheduledDate();
        Map<String, Object> up = new HashMap<>();
        up.put("userId", uid);
        up.put("date", meal.getScheduledDate());
        up.put(currentCategory, meal.getName());
        up.put(currentCategory + "Time", meal.getScheduledTime());
        up.put(currentCategory + "Image", meal.getImageUrl());

        db.collection("meal_plans").document(docId).set(up, SetOptions.merge())
                .addOnSuccessListener(a -> {

                    // --- NOTIFICATION UPDATE START ---
                    scheduleNotificationForMeal(meal.getScheduledDate(), meal.getScheduledTime(), meal.getName());
                    // --- NOTIFICATION UPDATE END ---

                    Toast.makeText(this, "Planned!", Toast.LENGTH_SHORT).show();
                    finish();
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
}