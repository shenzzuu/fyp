package com.example.planprep;

import static java.security.AccessController.getContext;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private BottomNavigationView bottomNavigationView;
    private ImageView ivNotification;
    private View badgeDot;
    private TextView tvGreeting;
    private TextView tvGreetingSmall; // "Good Morning,"
    private TextView tvGreetingBig;
    private ListenerRegistration userListener;

    // "Hero Card" (Today's Next Meal) UI
    private ImageView ivTodayMealImage;
    private TextView tvTodayMealName;
    private TextView tvTodayMealTime;
    private TextView tvNextMealCountdown;

    // Recommendations
    private LinearLayout recommendationContainer;
    private TextView tvRecommendationTitle;

    // Category Click Areas
    private LinearLayout layoutBreakfast, layoutLunch, layoutDinner, layoutSupper;

    // Quick Action Buttons
    private View btnCopyYesterday;
    private View btnSurpriseMe;
    private View btnClearDay;

    private MaterialButton btnMarkEaten;

    // Logic Variables
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private JSONObject localDataset;
    private CountDownTimer nextMealTimer;

    private String currentDocId = null;
    private String currentMealSlot = null;
    private String selectedDate;
    // Add to your class variables
    private String nextMealSlotName; // e.g., "lunch"
    private String nextMealTimeStr;  // e.g., "13:00"
    private boolean isTransitioning = false; // Prevents the infinite loop
    private android.os.CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Check Auth
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Setup UI
        initializeViews();
        setupClickListeners();
        setupNavigation();
        requestNotificationPermission();
        NotificationScheduler.scheduleDailyCheck(this);

        // Inside onCreate, before loading data
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        selectedDate = sdf.format(new Date());

        // Load Data
        loadLocalDataset();
        loadSavedMealPlan();
        showDailyRecommendations(); // Changed from showTimeBasedRecommendations
        checkUnreadNotifications();
        requestExactAlarmPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 1. Your Existing Logic (Reset Navigation)
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        // 2. New Logic: Check if User Enabled Alarms in Settings
        // This runs when the user comes back from the System Settings screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);

            // If the permission is now granted, save TRUE to Firestore
            if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                updateNotificationStatusInFirestore(true);
            }
        }
    }

    private void initializeViews() {
        // Top Header
        tvGreetingSmall = findViewById(R.id.tvGreetingSmall);
        tvGreetingBig = findViewById(R.id.tvGreetingBig);
        ivNotification = findViewById(R.id.ivNotification);
        badgeDot = findViewById(R.id.badgeDot);

        // Main Card (Hero)
        ivTodayMealImage = findViewById(R.id.ivTodayMealImage);
        tvTodayMealName = findViewById(R.id.tvTodayMealName);
        tvTodayMealTime = findViewById(R.id.tvTodayMealTime);
        tvNextMealCountdown = findViewById(R.id.tvNextMealCountdown);

        btnMarkEaten = findViewById(R.id.btnMarkEaten);

        // Categories
        layoutBreakfast = findViewById(R.id.layoutBreakfast);
        layoutLunch = findViewById(R.id.layoutLunch);
        layoutDinner = findViewById(R.id.layoutDinner);
        layoutSupper = findViewById(R.id.layoutSupper);

        // Recommendations
        recommendationContainer = findViewById(R.id.recommendationContainer);
        tvRecommendationTitle = findViewById(R.id.tvRecommendationTitle);

        // Quick Actions
        btnCopyYesterday = findViewById(R.id.btnCopyYesterday);
        btnSurpriseMe = findViewById(R.id.btnSurpriseMe);
        btnClearDay = findViewById(R.id.btnClearDay);
        // Navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation);
    }
    // --- 1. NEW: LISTEN FOR NAME CHANGES START ---
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startUserListener(currentUser);
        }
    }

    // --- 2. NEW: STOP LISTENING TO SAVE BATTERY ---
    @Override
    protected void onStop() {
        super.onStop();
        if (userListener != null) {
            userListener.remove();
        }
    }

    // --- 3. NEW: REAL-TIME DATA LOGIC ---
    private void startUserListener(FirebaseUser user) {
        userListener = db.collection("users").document(user.getUid())
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) return; // Ignore errors
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        updateGreetingUI(documentSnapshot, user);
                    }
                });
    }

    // --- 4. NEW: SPLIT GREETING UI LOGIC ---
    private void updateGreetingUI(DocumentSnapshot document, FirebaseUser user) {
        // A. SET TIME GREETING (Small Text)
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
        String timeGreeting;

        if (timeOfDay >= 0 && timeOfDay < 12) {
            timeGreeting = "Good Morning,";
        } else if (timeOfDay >= 12 && timeOfDay < 16) {
            timeGreeting = "Good Afternoon,";
        } else if (timeOfDay >= 16 && timeOfDay < 21) {
            timeGreeting = "Good Evening,";
        } else {
            timeGreeting = "Good Night,";
        }
        tvGreetingSmall.setText(timeGreeting);

        // B. SET NAME GREETING (Big Text)
        String displayNameToUse = "User"; // Default

        // Priority 1: Firestore (Real-time update)
        String dbName = document.getString("displayName");
        // Priority 2: Auth Profile
        String authName = user.getDisplayName();
        // Priority 3: Email fallback
        String emailName = null;
        if (user.getEmail() != null) {
            emailName = user.getEmail().split("@")[0].replace(".", " ");
            emailName = capitalizeWords(emailName);
        }

        if (dbName != null && !dbName.isEmpty()) {
            displayNameToUse = dbName;
        } else if (authName != null && !authName.isEmpty()) {
            displayNameToUse = authName;
        } else if (emailName != null) {
            displayNameToUse = emailName;
        }

        tvGreetingBig.setText(displayNameToUse + "!");
    }

    private void setupGreeting(FirebaseUser user) {
        // 1. Try to get the official Display Name first
        String displayName = user.getDisplayName();

        if (displayName != null && !displayName.isEmpty()) {
            // If Display Name exists, use it directly
            tvGreeting.setText("Hi, " + displayName + "!");
        } else {
            // 2. Fallback: Parse name from Email (Your original logic)
            String userEmail = user.getEmail();
            if (userEmail != null && !userEmail.isEmpty()) {
                String userName = userEmail.split("@")[0];
                userName = userName.replace(".", " ");
                tvGreeting.setText("Hi, " + capitalizeWords(userName) + "!");
            } else {
                // 3. Last Resort
                tvGreeting.setText("Welcome back!");
            }
        }
    }

    private void requestNotificationPermission() {
        // 1. Request Notification Permission (Required for Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // 2. Request Exact Alarm Permission (Required for Android 12+, Strictly Enforced on 14)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
            // Check if permission is missing
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                // Open the specific System Settings screen for the user to allow it
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                );
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void updateNotificationStatusInFirestore(boolean isEnabled) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(user.getUid())
                    .update("notificationsEnabled", isEnabled)
                    .addOnSuccessListener(aVoid -> android.util.Log.d("MealPlan", "Notification Status Updated: " + isEnabled))
                    .addOnFailureListener(e -> android.util.Log.e("MealPlan", "Failed to update status", e));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if this result is for our Notification Request (ID 101)
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // User clicked "Allow"
                updateNotificationStatusInFirestore(true);
            } else {
                // User clicked "Don't Allow"
                updateNotificationStatusInFirestore(false);
            }
        }
    }

    private void setupClickListeners() {
        // Notification Icon
        ivNotification.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotificationActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // Category Buttons
        layoutBreakfast.setOnClickListener(v -> openMealList("breakfast"));
        layoutLunch.setOnClickListener(v -> openMealList("lunch"));
        layoutDinner.setOnClickListener(v -> openMealList("dinner"));
        layoutSupper.setOnClickListener(v -> openMealList("supper"));

        // Inside setupClickListeners()
        btnMarkEaten.setOnClickListener(v -> {
            // 1. Validation: Ensure we have a loaded document and a valid meal slot
            if (currentDocId == null || currentMealSlot == null) {
                Toast.makeText(this, "No meal loaded to update", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. Update ONLY the specific "Eaten" field for the current meal
            // Example: If currentMealSlot is "lunch", this updates "lunchEaten" to true
            db.collection("meal_plans").document(currentDocId)
                    .update(currentMealSlot + "Eaten", true)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Great job! Meal completed.", Toast.LENGTH_SHORT).show();

                        // 3. Update UI immediately (Optional, but good for feedback)
                        // Note: Since you have a SnapshotListener running in loadSavedMealPlan(),
                        // the UI will actually update automatically via that listener anyway!
                        btnMarkEaten.setText("Completed");
                        btnMarkEaten.setEnabled(false);
                        btnMarkEaten.setBackgroundTintList(getResources().getColorStateList(android.R.color.darker_gray));
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                    });
        });

        // Quick Actions
        btnCopyYesterday.setOnClickListener(v -> copyYesterdayPlan());
        btnSurpriseMe.setOnClickListener(v -> surpriseMePlan());
        btnClearDay.setOnClickListener(v -> clearDayPlan());
    }

    // ----------------------- CORE LOGIC: DISPLAY NEXT MEAL -------------------------

    private void loadSavedMealPlan() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayDate = sdf.format(new Date());

        String specificDocId = userId + "_" + todayDate;

        // 2. Listen to THAT specific document
        db.collection("meal_plans").document(specificDocId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        return; // Handle error if needed
                    }

                    // 3. Check if document exists and has data
                    if (snapshot != null && snapshot.exists()) {
                        updateHeroCardWithNextMeal(snapshot);
                    } else {
                        // Document doesn't exist yet for today (User hasn't planned anything)
                        clearMealPlanUI();
                    }
                });
    }

    // 1. GENERATE 4 CARDS (One per category)
    private void showDailyRecommendations() {
        if (localDataset == null) return;

        // Clear previous cards
        recommendationContainer.removeAllViews();
        tvRecommendationTitle.setText("Daily Picks");

        String[] categories = {"breakfast", "lunch", "dinner", "supper"};

        // Loop through all 4 categories
        for (String cat : categories) {
            try {
                JSONArray meals = localDataset.getJSONArray(cat);
                if (meals.length() > 0) {
                    // Pick one random meal from this category
                    JSONObject meal = getRandomItem(meals);
                    addRecommendationCard(meal, cat);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 2. CREATE THE CARD UI (Name, Category, Origin
    private void addRecommendationCard(JSONObject meal, String category) {
        try {
            // 1. Parse Data
            String name = meal.getString("name");
            String imageUrl = meal.optString("image");
            String origin = meal.has("origin") ? meal.getString("origin") : "Malaysia";

            // 2. Create Card UI
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(500, 600);
            cardParams.setMargins(0, 8, 32, 8);
            card.setLayoutParams(cardParams);
            card.setCardElevation(8f);
            card.setRadius(24f);
            card.setClickable(true);

            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant));

            // Inner Container
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));

            // Image
            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 300
            );
            imageView.setLayoutParams(imageParams);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            loadImageFromUrl(imageUrl, imageView);

            // Text Container
            LinearLayout textContainer = new LinearLayout(this);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setPadding(24, 24, 24, 24);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));

            // -- Category Label --
            TextView tvCategory = new TextView(this);
            tvCategory.setText(category.toUpperCase());
            tvCategory.setTextSize(10f);
            tvCategory.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCategory.setTextColor(getCategoryColor(category));

            // -- Meal Name --
            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(16f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setMaxLines(2);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);

            // [UPDATED] Use ContextCompat to get the Day/Night adaptable color
            tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            // Weight to push origin to bottom
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0
            );
            nameParams.weight = 1.0f;
            tvName.setLayoutParams(nameParams);

            // -- Origin Label --
            TextView tvOrigin = new TextView(this);
            tvOrigin.setText("Origin: " + origin);
            tvOrigin.setTextSize(12f);

            // [UPDATED] Use ContextCompat for the secondary adaptable color
            tvOrigin.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvOrigin.setGravity(android.view.Gravity.BOTTOM);

            // Add views to container
            textContainer.addView(tvCategory);
            textContainer.addView(tvName);
            textContainer.addView(tvOrigin);

            inner.addView(imageView);
            inner.addView(textContainer);
            card.addView(inner);

            // Click Listener
            card.setOnClickListener(v -> {
                int defaultHour = 12;
                if (category.equals("breakfast")) defaultHour = 8;
                else if (category.equals("lunch")) defaultHour = 13;
                else if (category.equals("dinner")) defaultHour = 19;
                else if (category.equals("supper")) defaultHour = 22;

                com.google.android.material.timepicker.MaterialTimePicker timePicker =
                        new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                                .setHour(defaultHour)
                                .setMinute(0)
                                .setTitleText("Set time for " + name)
                                .setInputMode(com.google.android.material.timepicker.MaterialTimePicker.INPUT_MODE_CLOCK)
                                .build();

                timePicker.addOnPositiveButtonClickListener(dialog -> {
                    String pickedTime = String.format(Locale.getDefault(), "%02d:%02d", timePicker.getHour(), timePicker.getMinute());
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    String todayDate = sdf.format(new Date());
                    saveRecommendationToPlan(todayDate, pickedTime, category, name, imageUrl);
                });

                timePicker.show(getSupportFragmentManager(), "TIME_PICKER_TAG");
            });

            recommendationContainer.addView(card);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Helper for Category Colors ---
// This manually checks for Night Mode to switch between "Bold" and "Pastel" colors
    private int getCategoryColor(String category) {
        int nightModeFlags = getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNightMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isNightMode) {
            // NIGHT MODE: Pastel/Lighter colors (easier to read on Ash background)
            if (category.equals("breakfast")) return 0xFFFFB74D; // Light Orange
            if (category.equals("lunch"))     return 0xFF81C784; // Pastel Green
            if (category.equals("dinner"))    return 0xFFE57373; // Soft Red
            return 0xFF9575CD;                                   // Lavender
        } else {
            // DAY MODE: Bold/Darker colors (easier to read on White background)
            if (category.equals("breakfast")) return 0xFFFB8C00;
            if (category.equals("lunch"))     return 0xFF43A047;
            if (category.equals("dinner"))    return 0xFFE53935;
            return 0xFF5E35B1;
        }
    }

    private void updateHeroCardWithNextMeal(com.google.firebase.firestore.DocumentSnapshot doc) {
        currentDocId = doc.getId();

        String[] mealKeys = {"breakfast", "lunch", "dinner", "supper"};
        String[] timeKeys = {"breakfastTime", "lunchTime", "dinnerTime", "supperTime"};
        String[] imgKeys = {"breakfastImage", "lunchImage", "dinnerImage", "supperImage"};

        // 1. Determine Starting Slot based on Current Time
        java.util.Calendar now = java.util.Calendar.getInstance();
        int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);

        int index = 0; // Default to Breakfast
        if (currentHour >= 11 && currentHour < 16) index = 1;      // Lunch
        else if (currentHour >= 16 && currentHour < 21) index = 2; // Dinner
        else if (currentHour >= 21) index = 3;                     // Supper

        // 2. FAST-FORWARD LOGIC: Find the first ACTUAL, ACTIVE meal
        // 2. FAST-FORWARD LOGIC: Find the first ACTUAL, ACTIVE meal
        boolean foundValidMeal = false;

        while (index < 4) {
            String slot = mealKeys[index];
            String name = doc.getString(slot);
            String timeStr = doc.getString(timeKeys[index]); // Get the time for this specific slot

            Boolean isEaten = doc.getBoolean(slot + "Eaten");
            if (isEaten == null) isEaten = false;

            Boolean isMissed = doc.getBoolean(slot + "Missed");
            if (isMissed == null) isMissed = false;

            // --- SMART CHECK: AUTO-CORRECT "MISSED" ---
            // If it says "Missed" in the database, but the time is actually in the future
            // (e.g., you just updated the time), we should ignore the Missed flag.
            if (isMissed && isTimeInFuture(timeStr)) {
                isMissed = false;
                // Optional: Update database to fix the flag permanently
                doc.getReference().update(slot + "Missed", false);
            }

            // CHECK: Is this slot finished or empty?
            if (isEaten || isMissed || name == null || name.isEmpty()) {
                index++; // Skip to next meal
            } else {
                // We found a valid, upcoming meal! Stop looking.
                foundValidMeal = true;
                break;
            }
        }

        // 3. Handle "End of Day" (No valid meals left)
        if (!foundValidMeal || index > 3) {
            tvTodayMealName.setText("All Meals Done!");
            tvTodayMealTime.setText("See you tomorrow");

            ivTodayMealImage.setImageResource(android.R.color.transparent);
            ivTodayMealImage.setBackgroundColor(0xFFCCCCCC); // Placeholder

            btnMarkEaten.setVisibility(View.GONE);

            if (nextMealTimer != null) {
                nextMealTimer.cancel();
                nextMealTimer = null;
            }
            tvNextMealCountdown.setText("End of Day");
            return;
        }

        // 4. Update Global Variables & UI
        currentMealSlot = mealKeys[index];
        String mealName = doc.getString(mealKeys[index]);
        String mealTime = doc.getString(timeKeys[index]); // This is 24H format (e.g. "13:30")
        String mealImg = doc.getString(imgKeys[index]);

        tvTodayMealName.setText(mealName);
        tvTodayMealTime.setText("Today at " + mealTime);

        // Image Handling
        if (mealImg != null && !mealImg.isEmpty()) {
            loadImageFromUrl(mealImg, ivTodayMealImage);
        } else {
            ivTodayMealImage.setImageResource(R.drawable.meal); // Placeholder
        }

        btnMarkEaten.setVisibility(View.VISIBLE);

        // 5. Sync the In-App Timer
        updateButtonState(mealTime, false);
        startCountdown(mealTime, mealName);

        // --- 6. SCHEDULE SYSTEM ALARM (Fixes "No notification when closed") ---
        try {
            java.text.SimpleDateFormat dateSdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String todayDateStr = dateSdf.format(new java.util.Date());

            // This ensures the alarm rings even if the app is closed
            scheduleNotificationForMeal(todayDateStr, mealTime, mealName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private boolean isTimeInFuture(String timeStr) {
        if (timeStr == null) return false;
        try {
            java.util.Date dateObj = null;
            // Try 24-hour format
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                dateObj = sdf.parse(timeStr);
            } catch (Exception e) {
                // Try 12-hour format
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
                dateObj = sdf.parse(timeStr);
            }

            if (dateObj == null) return false;

            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar mealTime = java.util.Calendar.getInstance();
            mealTime.setTime(dateObj);

            // Set mealTime to Today
            mealTime.set(java.util.Calendar.YEAR, now.get(java.util.Calendar.YEAR));
            mealTime.set(java.util.Calendar.MONTH, now.get(java.util.Calendar.MONTH));
            mealTime.set(java.util.Calendar.DAY_OF_MONTH, now.get(java.util.Calendar.DAY_OF_MONTH));

            // If mealTime is after now, return true
            return mealTime.after(now);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateButtonState(String mealTimeStr, boolean isEaten) {
        if (isEaten) {
            btnMarkEaten.setText("Completed");
            btnMarkEaten.setIconResource(R.drawable.check);
            btnMarkEaten.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF757575)); // Grey
            btnMarkEaten.setEnabled(false);
            btnMarkEaten.setVisibility(View.VISIBLE);
            return;
        }

        // 1. Define both formats
        // Format A: "13:00" (Used by Surprise Me)
        SimpleDateFormat format24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
        // Format B: "01:00 PM" (Used by manual TimePicker)
        SimpleDateFormat format12 = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        Date dateObj = null;

        // 2. Try parsing
        try {
            // Try 24H first
            dateObj = format24.parse(mealTimeStr);
        } catch (ParseException e1) {
            try {
                // If failed, try 12H
                dateObj = format12.parse(mealTimeStr);
            } catch (ParseException e2) {
                // Both failed? Hide button and stop.
                btnMarkEaten.setVisibility(View.GONE);
                return;
            }
        }

        try {
            // 3. Logic: Compare Times
            Calendar mealTime = Calendar.getInstance();
            Calendar timeParser = Calendar.getInstance();
            timeParser.setTime(dateObj);

            // Apply the parsed Hour/Minute to "Today"
            mealTime.set(Calendar.HOUR_OF_DAY, timeParser.get(Calendar.HOUR_OF_DAY));
            mealTime.set(Calendar.MINUTE, timeParser.get(Calendar.MINUTE));
            mealTime.set(Calendar.SECOND, 0);

            Calendar now = Calendar.getInstance();

            if (now.before(mealTime)) {
                // Case: Too Early
                btnMarkEaten.setText("Wait for Meal Time");
                btnMarkEaten.setIcon(null);
                btnMarkEaten.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFBDBDBD)); // Light Grey
                btnMarkEaten.setEnabled(false);
            } else {
                // Case: It is Time!
                btnMarkEaten.setText("I have eaten");
                btnMarkEaten.setIconResource(R.drawable.check);
                btnMarkEaten.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF43A047)); // Green
                btnMarkEaten.setEnabled(true);
            }

            // Ensure button is visible if logic succeeds
            btnMarkEaten.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            e.printStackTrace();
            btnMarkEaten.setVisibility(View.GONE);
        }
    }

    private void clearMealPlanUI() {
        tvTodayMealName.setText("No Plan Today");
        tvTodayMealTime.setText("-- : --");

        // 1. Set the Placeholder Image (Instead of Transparent)
        // Make sure 'ic_launcher_background' exists, or change this to your own drawable name
        ivTodayMealImage.setImageResource(R.drawable.ic_launcher_background);

        // 2. Clear any background color so the image looks clean
        ivTodayMealImage.setBackground(null);

        // 3. Reset Timer & Text
        if (nextMealTimer != null) nextMealTimer.cancel();
        tvNextMealCountdown.setText("Use Quick Actions to start!");

        // 4. Hide Button
        btnMarkEaten.setVisibility(View.GONE);
    }

    // PHASE 1: STANDARD COUNTDOWN
    private void startCountdown(String targetTimeString, String mealName) {
        if (nextMealTimer != null) {
            nextMealTimer.cancel();
            nextMealTimer = null;
        }

        try {
            java.util.Date dateObj = null;

            // 1. Try Specific "12:30 AM" Format
            try {
                java.text.SimpleDateFormat sdf12 = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
                dateObj = sdf12.parse(targetTimeString);
            } catch (Exception e) { }

            // 2. Try Standard "12:30" or "00:30" Format
            if (dateObj == null) {
                try {
                    java.text.SimpleDateFormat sdf24 = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                    dateObj = sdf24.parse(targetTimeString);

                    // --- AMBIGUITY FIX for "12:xx" ---
                    // If parsed as 12:00 PM (Noon) but we are currently at Midnight (00:xx - 05:xx),
                    // assume the user meant Midnight (00:xx).
                    java.util.Calendar check = java.util.Calendar.getInstance();
                    check.setTime(dateObj);

                    int parsedHour = check.get(java.util.Calendar.HOUR_OF_DAY);
                    int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);

                    if (parsedHour == 12 && currentHour < 6) {
                        check.set(java.util.Calendar.HOUR_OF_DAY, 0); // Convert 12 PM -> 00 AM
                        dateObj = check.getTime();
                    }
                } catch (Exception e2) { }
            }

            if (dateObj == null) {
                tvNextMealCountdown.setText("Error time format");
                return;
            }

            java.util.Calendar mealTime = java.util.Calendar.getInstance();
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar timeParser = java.util.Calendar.getInstance();
            timeParser.setTime(dateObj);

            mealTime.set(java.util.Calendar.HOUR_OF_DAY, timeParser.get(java.util.Calendar.HOUR_OF_DAY));
            mealTime.set(java.util.Calendar.MINUTE, timeParser.get(java.util.Calendar.MINUTE));
            mealTime.set(java.util.Calendar.SECOND, 0);

            long graceDurationMillis = 60 * 60 * 1000; // 60 Minutes Grace
            long mealTimeMillis = mealTime.getTimeInMillis();
            long graceEndTimeMillis = mealTimeMillis + graceDurationMillis;
            long currentTimeMillis = now.getTimeInMillis();

            // SCENARIO 1: FUTURE
            if (currentTimeMillis < mealTimeMillis) {
                long millisUntilMeal = mealTimeMillis - currentTimeMillis;
                nextMealTimer = new android.os.CountDownTimer(millisUntilMeal, 1000) {
                    public void onTick(long ms) {
                        long h = ms / 3600000;
                        long m = (ms % 3600000) / 60000;
                        long s = (ms % 60000) / 1000;
                        tvNextMealCountdown.setText(String.format(java.util.Locale.getDefault(), "Ready in: %02d : %02d : %02d", h, m, s));
                        tvNextMealCountdown.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                    }
                    public void onFinish() {
                        updateButtonState(targetTimeString, false);
                        startCountdown(targetTimeString, mealName);
                    }
                }.start();
            }
            // SCENARIO 2: GRACE PERIOD
            else if (currentTimeMillis < graceEndTimeMillis) {
                updateButtonState(targetTimeString, false);
                btnMarkEaten.setVisibility(View.VISIBLE);

                long millisLeftInGrace = graceEndTimeMillis - currentTimeMillis;
                tvNextMealCountdown.setText("It's Time! (Late)");
                tvNextMealCountdown.setTextColor(android.graphics.Color.RED);

                nextMealTimer = new android.os.CountDownTimer(millisLeftInGrace, 1000) {
                    public void onTick(long ms) {
                        tvNextMealCountdown.setText("It's Time! (Late)");
                    }
                    public void onFinish() {
                        if (!isTransitioning) markMealAsMissed();
                    }
                }.start();
            }
            // SCENARIO 3: MISSED
            else {
                if (!isTransitioning) markMealAsMissed();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // PHASE 4: AUTO-MARK MISSED
    private void markMealAsMissed() {
        // 1. STOP if we are already transitioning or data is missing
        if (isTransitioning || currentDocId == null || currentMealSlot == null) return;

        // 2. LOCK the process
        isTransitioning = true;

        Map<String, Object> updates = new HashMap<>();
        updates.put(currentMealSlot + "Eaten", false);
        updates.put(currentMealSlot + "Missed", true);

        db.collection("meal_plans").document(currentDocId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // ... (Your existing Success Logic: Toast, UI update, Next Meal setup) ...

                    // Example of your logic:
                    if (nextMealTimeStr != null) {
                        Toast.makeText(this, "Transitioning to next meal...", Toast.LENGTH_SHORT).show();
                        // ... set texts ...
                        startCountdown(nextMealTimeStr, nextMealSlotName);
                    } else {
                        tvNextMealCountdown.setText("No more meals");
                    }

                    // 3. IMPORTANT: Unlock after a delay to allow UI to settle
                    new android.os.Handler().postDelayed(() -> isTransitioning = false, 2000);
                })
                .addOnFailureListener(e -> {
                    isTransitioning = false; // Unlock immediately on failure
                });
    }

    // Helper
    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.length() > 0) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ----------------------- QUICK ACTIONS -------------------------

    private void copyYesterdayPlan() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();

        // 1. Calculate Dates for IDs (Yesterday & Today)
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdfId = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat sdfDayName = new SimpleDateFormat("EEEE", Locale.getDefault());

        String todayDate = sdfId.format(cal.getTime());
        String todayDayName = sdfDayName.format(cal.getTime());
        String targetDocId = userId + "_" + todayDate;

        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayDate = sdfId.format(cal.getTime());
        String sourceDocId = userId + "_" + yesterdayDate;

        // 2. Fetch Yesterday's Document
        db.collection("meal_plans").document(sourceDocId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && snapshot.getData() != null) {

                        // 3. Get Data and Modify for Today
                        Map<String, Object> data = snapshot.getData();
                        data.put("date", todayDate);
                        data.put("day", todayDayName);
                        data.put("timestamp", new Date());

                        String[] slots = {"breakfast", "lunch", "dinner", "supper"};

                        // Reset Statuses
                        for (String slot : slots) {
                            if (data.containsKey(slot)) {
                                data.put(slot + "Eaten", false);
                                data.put(slot + "Missed", false);
                            }
                        }

                        // 4. Save to Today's Document ID
                        db.collection("meal_plans").document(targetDocId)
                                .set(data)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Plan copied from yesterday!", Toast.LENGTH_SHORT).show();

                                    // --- NOTIFICATION UPDATE START ---
                                    // Schedule notifications for all copied meals
                                    for (String slot : slots) {
                                        if (data.containsKey(slot) && data.containsKey(slot + "Time")) {
                                            String mealName = (String) data.get(slot);
                                            String mealTime = (String) data.get(slot + "Time");
                                            scheduleNotificationForMeal(todayDate, mealTime, mealName);
                                        }
                                    }
                                    // --- NOTIFICATION UPDATE END ---
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to copy plan", Toast.LENGTH_SHORT).show());

                    } else {
                        Toast.makeText(this, "No plan found for yesterday (" + yesterdayDate + ")", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show());
    }

    private void surpriseMePlan() {
        // 1. Safety Checks
        if (localDataset == null) {
            Toast.makeText(this, "Data loading... try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // 2. Construct Today's ID to check existence
        String userId = user.getUid();
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayDate = sdfDate.format(new Date());
        String specificDocId = userId + "_" + todayDate;

        // 3. Check Firebase BEFORE generating
        db.collection("meal_plans").document(specificDocId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        // STOP: A plan already exists (even if it's just one meal)
                        Toast.makeText(this, "You already have a plan for today!", Toast.LENGTH_SHORT).show();
                    } else {
                        // PROCEED: No plan exists, generate the surprise
                        performSurpriseGeneration(user, specificDocId, todayDate);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error checking status", Toast.LENGTH_SHORT).show());
    }

    // Helper method to keep the logic clean
    private void performSurpriseGeneration(FirebaseUser user, String docId, String dateString) {
        try {
            // A. Pick Random items
            JSONObject rBreakfast = getRandomItem(localDataset.getJSONArray("breakfast"));
            JSONObject rLunch = getRandomItem(localDataset.getJSONArray("lunch"));
            JSONObject rDinner = getRandomItem(localDataset.getJSONArray("dinner"));
            JSONObject rSupper = getRandomItem(localDataset.getJSONArray("supper"));

            String bName = rBreakfast.getString("name");
            String lName = rLunch.getString("name");
            String dName = rDinner.getString("name");
            String sName = rSupper.getString("name");

            // B. Prepare Data Map
            Map<String, Object> plan = new HashMap<>();
            plan.put("userId", user.getUid());
            plan.put("day", getCurrentDay());
            plan.put("date", dateString);
            plan.put("timestamp", new Date());

            // C. Populate Meals
            plan.put("breakfast", bName);
            plan.put("breakfastImage", rBreakfast.optString("image"));
            plan.put("breakfastTime", "08:00");
            plan.put("breakfastEaten", false);

            plan.put("lunch", lName);
            plan.put("lunchImage", rLunch.optString("image"));
            plan.put("lunchTime", "13:00");
            plan.put("lunchEaten", false);

            plan.put("dinner", dName);
            plan.put("dinnerImage", rDinner.optString("image"));
            plan.put("dinnerTime", "19:00");
            plan.put("dinnerEaten", false);

            plan.put("supper", sName);
            plan.put("supperImage", rSupper.optString("image"));
            plan.put("supperTime", "22:00");
            plan.put("supperEaten", false);

            // D. Save directly
            db.collection("meal_plans").document(docId)
                    .set(plan)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Surprise! Meal plan generated.", Toast.LENGTH_SHORT).show();

                        // --- NOTIFICATION UPDATE START ---
                        scheduleNotificationForMeal(dateString, "08:00", bName);
                        scheduleNotificationForMeal(dateString, "13:00", lName);
                        scheduleNotificationForMeal(dateString, "19:00", dName);
                        scheduleNotificationForMeal(dateString, "22:00", sName);

                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        if (dateString.equals(sdf.format(new Date()))) {
                            loadSavedMealPlan();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save surprise plan.", Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing data", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearDayPlan() {
        // 1. Create the BottomSheetDialog
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        // 2. Inflate your specific layout
        View sheetView = getLayoutInflater().inflate(R.layout.layout_clear_day, null);
        bottomSheetDialog.setContentView(sheetView);

        // 3. Initialize Views from the sheet
        TextView tvTitle = sheetView.findViewById(R.id.tvDeleteTitle);
        com.google.android.material.button.MaterialButton btnConfirm = sheetView.findViewById(R.id.btnConfirmDelete);
        com.google.android.material.button.MaterialButton btnCancel = sheetView.findViewById(R.id.btnCancelDelete);

        // 4. Customize the Title (Optional, since XML says "Delete this item?")
        tvTitle.setText("Clear entire day's plan?");

        // 5. Set Listener: Confirm Delete
        btnConfirm.setOnClickListener(v -> {
            // --- ORIGINAL DELETE LOGIC MOVED HERE ---
            performDeleteDayPlan();
            bottomSheetDialog.dismiss();
        });

        // 6. Set Listener: Cancel
        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());

        // 7. Show the Sheet
        bottomSheetDialog.show();
    }

    // Helper method containing the actual Firebase logic
    private void performDeleteDayPlan() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayDate = sdf.format(new Date());
        String specificDocId = userId + "_" + todayDate;

        db.collection("meal_plans").document(specificDocId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Plan cleared successfully", Toast.LENGTH_SHORT).show();
                    clearMealPlanUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to clear plan", Toast.LENGTH_SHORT).show();
                });
    }

    private JSONObject getRandomItem(JSONArray array) throws Exception {
        if (array.length() == 0) return new JSONObject();
        int idx = new Random().nextInt(array.length());
        return array.getJSONObject(idx);
    }

    // ----------------------- RECOMMENDATION LOGIC -------------------------

    private void loadLocalDataset() {
        try {
            InputStream is = getAssets().open("malaysian_food.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            localDataset = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load dataset", Toast.LENGTH_LONG).show();
        }
    }

    // ----------------------- HELPER METHODS -------------------------

    private void openMealList(String category) {
        try {
            if (localDataset == null) return;
            JSONArray mealsArray = localDataset.getJSONArray(category);
            Intent intent = new Intent(MainActivity.this, MealListActivity.class);
            intent.putExtra("category", category);
            intent.putExtra("meals_json", mealsArray.toString());
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } catch (Exception e) {
            Toast.makeText(this, "Error loading meals", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkUnreadNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();

        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    int unread = snap.size();
                    if (badgeDot != null) {
                        badgeDot.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void requestExactAlarmPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }

    // COPY THIS HELPER METHOD INTO YOUR ACTIVITY CLASS
    // --- PASTE THIS METHOD INTO HOMEFRAGMENT ---
    private void scheduleNotificationForMeal(String dateStr, String timeStr, String mealName) {
        if (dateStr == null || timeStr == null || mealName == null) return;

        try {
            java.util.Date date = null;
            String fullDateTime = dateStr + " " + timeStr;

            // 1. Try Specific Format (e.g. "01:00 PM")
            try {
                java.text.SimpleDateFormat sdf12 = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault());
                date = sdf12.parse(fullDateTime);
            } catch (Exception e) { }

            // 2. Try Standard Format (e.g. "13:00" or "1:00")
            if (date == null) {
                try {
                    java.text.SimpleDateFormat sdf24 = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                    date = sdf24.parse(fullDateTime);
                } catch (Exception e2) { }
            }

            if (date != null) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);
                int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                String name = mealName.toLowerCase().trim();

                // --- SMART CONTEXT LOGIC ---

                // CASE 1: BREAKFAST (Morning / Early AM)
                // Fix: If user types "12:30" (Noon), they likely meant "00:30" (Midnight)
                if (name.equals("breakfast")) {
                    if (hour == 12) {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); // 12 PM -> 0 AM
                    }
                }

                // CASE 2: LUNCH (Afternoon)
                // Fix: If user types "1:00" (AM), they meant "13:00" (1 PM)
                // Range: 1 AM to 4 AM are converted to PM.
                else if (name.equals("lunch")) {
                    if (hour >= 1 && hour <= 4) {
                        cal.add(java.util.Calendar.HOUR_OF_DAY, 12);
                    }
                }

                // CASE 3: DINNER (Evening)
                // Fix: If user types "7:00" (AM), they meant "19:00" (7 PM)
                // Range: Any time from 1 AM to 11 AM becomes PM.
                else if (name.equals("dinner")) {
                    if (hour >= 1 && hour < 12) {
                        cal.add(java.util.Calendar.HOUR_OF_DAY, 12);
                    }
                }

                // CASE 4: SUPPER (Late Night)
                // Fix A: "10:00" (AM) -> "22:00" (10 PM)
                // Fix B: "1:00" (AM) -> Remains 1:00 AM (Late night snack)
                else if (name.equals("supper")) {
                    // Only convert hours 6 AM to 11 AM into PM.
                    // We assume 1 AM - 5 AM are legitimate "Late Night / Early Morning" suppers.
                    if (hour >= 6 && hour < 12) {
                        cal.add(java.util.Calendar.HOUR_OF_DAY, 12);
                    }
                    // Handle the 12:xx Noon vs Midnight ambiguity for Supper too
                    if (hour == 12) {
                        // "12:30" usually means 12:30 AM (Midnight) for supper, not Lunch time.
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    }
                }

                // Update the date object with corrections
                date = cal.getTime();

                // --- FINAL CHECK: IS IT IN THE PAST? ---
                // If the time is passed (e.g. it's 2 PM and we scheduled 1 PM),
                // maybe they meant TOMORROW? (Optional: Enable if you want auto-rollover)
                if (date.getTime() < System.currentTimeMillis()) {
                    // Optional: cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                    // date = cal.getTime();
                    // android.util.Log.d("MealPlan", "Time passed, moved to tomorrow.");
                }

                // Schedule only if future
                if (date.getTime() > System.currentTimeMillis()) {
                    int uniqueId = (mealName + date.getTime()).hashCode();

                    com.example.planprep.NotificationScheduler.scheduleMealNotifications(
                            this,
                            uniqueId,
                            mealName,
                            date.getTime()
                    );

                    android.util.Log.d("MealPlan", "Scheduled: " + mealName + " at " + date.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;

            Intent intent = null;
            if (id == R.id.nav_meal_plan) intent = new Intent(this, MealPlanActivity.class);
            else if (id == R.id.nav_cart) intent = new Intent(this, CartActivity.class);
            else if (id == R.id.nav_profile) intent = new Intent(this, UserProfileActivity.class);

            if (intent != null) {
                startActivity(intent);
                // Custom Animation: Slide IN from Right, Slide OUT to Left
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
            return true;
        });
    }

    private String getCurrentDay() {
        Calendar c = Calendar.getInstance();
        return getDayString(c.get(Calendar.DAY_OF_WEEK));
    }

    private String getDayString(int dayInt) {
        switch (dayInt) {
            case Calendar.MONDAY: return "Monday";
            case Calendar.TUESDAY: return "Tuesday";
            case Calendar.WEDNESDAY: return "Wednesday";
            case Calendar.THURSDAY: return "Thursday";
            case Calendar.FRIDAY: return "Friday";
            case Calendar.SATURDAY: return "Saturday";
            default: return "Sunday";
        }
    }

    // UPDATED: Now accepts 'dateToSave' as a parameter
    private void saveRecommendationToPlan(String dateToSave, String timeToSave, String category, String mealName, String imageUrl) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = user.getUid();
        String specificDocId = userId + "_" + dateToSave;

        // 1. Calculate Day Name
        String dayName = "";
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = inFormat.parse(dateToSave);
            SimpleDateFormat outFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            dayName = outFormat.format(date);
        } catch (Exception e) { e.printStackTrace(); }

        // 2. Prepare Data
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("userId", userId);
        updateMap.put("day", dayName);
        updateMap.put("date", dateToSave);
        updateMap.put("timestamp", new Date());

        updateMap.put(category, mealName);
        updateMap.put(category + "Image", imageUrl);
        updateMap.put(category + "Time", timeToSave);
        updateMap.put(category + "Eaten", false);

        // 3. Save DIRECTLY (More reliable)
        db.collection("meal_plans").document(specificDocId)
                .set(updateMap, SetOptions.merge()) // Merges if exists, creates if not
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Added " + mealName, Toast.LENGTH_SHORT).show();

                    // Schedule Notification
                    scheduleNotificationForMeal(dateToSave, timeToSave, mealName);

                    // Refresh UI if it's today
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    if (dateToSave.equals(sdf.format(new Date()))) {
                        loadSavedMealPlan();
                    }
                })
                .addOnFailureListener(e -> {
                    // IMPORTANT: This tells you WHY it failed in the logs
                    Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("FirebaseError", e.getMessage());
                });
    }


    private void loadImageFromUrl(String url, ImageView imageView) {
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            try {
                InputStream input = new URL(url).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                imageView.post(() -> imageView.setImageBitmap(bitmap));
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}