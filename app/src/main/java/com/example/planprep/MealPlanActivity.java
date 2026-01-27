package com.example.planprep;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class MealPlanActivity extends AppCompatActivity {

    // UI Components
    private TextView tvDateDisplay, tvTodayBadge;
    private ImageView btnPrevDay, btnNextDay;
    private LinearLayout dateInfoContainer;
    private BottomNavigationView bottomNavigationView;

    // Meal Cards Containers
    private CardView cardBreakfast, cardLunch, cardDinner, cardSupper;

    // Meal Content Views
    private TextView tvBreakfastName, tvBreakfastTime, tvLunchName, tvLunchTime, tvDinnerName, tvDinnerTime, tvSupperName, tvSupperTime;
    private ImageView ivBreakfastImage, ivLunchImage, ivDinnerImage, ivSupperImage;

    // Logic Variables
    private Calendar selectedDate;
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat idDateFormat;

    // Firebase
    private FirebaseFirestore db;
    private String uid;
    private ListenerRegistration firestoreListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_plan);

        // 1. Initialize Logic & Firebase
        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        selectedDate = Calendar.getInstance();
        displayDateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        idDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // 2. Bind Views
        initViews();

        // 3. Initial Display & Data Load
        updateDateDisplayAndLoadData();

        // 4. Listeners
        setupDateNavigation();
        setupNavigation();
        setupMealClickListeners();
    }

    // --- FIX: Add onResume to fix Navigation Highlighting ---
    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_meal_plan);
        }
    }

    private void initViews() {
        tvDateDisplay = findViewById(R.id.tvDateDisplay);
        tvTodayBadge = findViewById(R.id.tvTodayBadge);

        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        dateInfoContainer = findViewById(R.id.dateInfoContainer);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Card Containers
        cardBreakfast = findViewById(R.id.cardBreakfast);
        cardLunch = findViewById(R.id.cardLunch);
        cardDinner = findViewById(R.id.cardDinner);
        cardSupper = findViewById(R.id.cardSupper);

        // Internal Views
        tvBreakfastName = findViewById(R.id.tvBreakfastName);
        tvBreakfastTime = findViewById(R.id.tvBreakfastTime);
        ivBreakfastImage = findViewById(R.id.ivBreakfastImage);

        tvLunchName = findViewById(R.id.tvLunchName);
        tvLunchTime = findViewById(R.id.tvLunchTime);
        ivLunchImage = findViewById(R.id.ivLunchImage);

        tvDinnerName = findViewById(R.id.tvDinnerName);
        tvDinnerTime = findViewById(R.id.tvDinnerTime);
        ivDinnerImage = findViewById(R.id.ivDinnerImage);

        tvSupperName = findViewById(R.id.tvSupperName);
        tvSupperTime = findViewById(R.id.tvSupperTime);
        ivSupperImage = findViewById(R.id.ivSupperImage);
    }

    // --- NAVIGATION LOGIC ---
    private void setupNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_meal_plan) return true;

            // 1. GOING BACKWARD (To Home)
            if (id == R.id.nav_home) {
                finish(); // Close this to reveal Home underneath
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return true;
            }

            Intent intent = null;
            if (id == R.id.nav_cart) intent = new Intent(this, CartActivity.class);
            else if (id == R.id.nav_profile) intent = new Intent(this, UserProfileActivity.class);

            // 2. GOING FORWARD (To Cart or Profile)
            if (intent != null) {
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                finish(); // Close this activity
            }
            return true;
        });
    }

    // Handle Physical Back Button
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        // Use the "Reverse" animation (slide out to right)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // --- DATE LOGIC ---
    private void setupDateNavigation() {
        btnPrevDay.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1);
            updateDateDisplayAndLoadData();
        });

        btnNextDay.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_YEAR, 1);
            updateDateDisplayAndLoadData();
        });

        dateInfoContainer.setOnClickListener(v -> showMaterialDatePicker());
    }

    private void updateDateDisplayAndLoadData() {
        tvDateDisplay.setText(displayDateFormat.format(selectedDate.getTime()));

        Calendar today = Calendar.getInstance();
        boolean isToday =
                today.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                        today.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR);
        tvTodayBadge.setVisibility(isToday ? View.VISIBLE : View.INVISIBLE);

        loadMealPlanForSelectedDate();
    }

    private void showMaterialDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);

            selectedDate.set(Calendar.YEAR, calendar.get(Calendar.YEAR));
            selectedDate.set(Calendar.MONTH, calendar.get(Calendar.MONTH));
            selectedDate.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH));

            updateDateDisplayAndLoadData();
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    // --- FIREBASE DATA LOADING ---
    private void loadMealPlanForSelectedDate() {
        if (uid == null) return;

        if (firestoreListener != null) {
            firestoreListener.remove();
        }

        String dateId = idDateFormat.format(selectedDate.getTime());
        String docId = uid + "_" + dateId;

        firestoreListener = db.collection("meal_plans").document(docId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error loading plan", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        updateCardUI(documentSnapshot, "breakfast", tvBreakfastName, tvBreakfastTime, ivBreakfastImage);
                        updateCardUI(documentSnapshot, "lunch", tvLunchName, tvLunchTime, ivLunchImage);
                        updateCardUI(documentSnapshot, "dinner", tvDinnerName, tvDinnerTime, ivDinnerImage);
                        updateCardUI(documentSnapshot, "supper", tvSupperName, tvSupperTime, ivSupperImage);
                    } else {
                        resetCardUI("Breakfast", tvBreakfastName, tvBreakfastTime, ivBreakfastImage);
                        resetCardUI("Lunch", tvLunchName, tvLunchTime, ivLunchImage);
                        resetCardUI("Dinner", tvDinnerName, tvDinnerTime, ivDinnerImage);
                        resetCardUI("Supper", tvSupperName, tvSupperTime, ivSupperImage);
                    }
                });
    }

    private void updateCardUI(DocumentSnapshot doc, String key, TextView tvName, TextView tvTime, ImageView ivImage) {
        String name = doc.getString(key);
        String time = doc.getString(key + "Time");
        String image = doc.getString(key + "Image");

        if (name != null && !name.isEmpty()) {
            tvName.setText(name);
            tvTime.setText(time != null ? time : "");
            tvTime.setVisibility(View.VISIBLE);

            if (image != null && !image.isEmpty()) {
                Glide.with(this).load(image).into(ivImage);
            } else {
                ivImage.setImageResource(R.drawable.ic_launcher_background);
            }
        } else {
            resetCardUI(key.substring(0, 1).toUpperCase() + key.substring(1), tvName, tvTime, ivImage);
        }
    }

    private void resetCardUI(String defaultName, TextView tvName, TextView tvTime, ImageView ivImage) {
        tvName.setText("Add " + defaultName);
        tvTime.setText("");
        tvTime.setVisibility(View.GONE);
        ivImage.setImageResource(R.drawable.meal);
        ivImage.setBackgroundColor(0xFFEEEEEE);
    }

    private void setupMealClickListeners() {
        View.OnClickListener mealClickListener = v -> {
            String mealType = "";
            int id = v.getId();

            if (id == R.id.cardBreakfast) mealType = "breakfast";
            else if (id == R.id.cardLunch) mealType = "lunch";
            else if (id == R.id.cardDinner) mealType = "dinner";
            else if (id == R.id.cardSupper) mealType = "supper";

            Intent intent = new Intent(MealPlanActivity.this, AddFoodActivity.class);

            // Pass Data
            intent.putExtra("MEAL_TYPE", mealType);
            String dateId = idDateFormat.format(selectedDate.getTime());
            intent.putExtra("DATE_ID", dateId);
            intent.putExtra("DATE_DISPLAY", displayDateFormat.format(selectedDate.getTime()));

            startActivity(intent);

            // --- ADD THIS LINE HERE ---
            // Applies the "Forward" animation (New screen slides in from Right)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        };

        cardBreakfast.setOnClickListener(mealClickListener);
        cardLunch.setOnClickListener(mealClickListener);
        cardDinner.setOnClickListener(mealClickListener);
        cardSupper.setOnClickListener(mealClickListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}