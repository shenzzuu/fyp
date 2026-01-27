package com.example.planprep;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MealStatusHistoryActivity extends AppCompatActivity {

    private RecyclerView rvMealStatus;
    private MealStatusAdapter adapter;
    private ChipGroup chipGroupStatus;
    private ProgressBar pbLoading;
    private ImageView btnBack;

    private LinearLayout layoutEmptyState;

    private List<MealStatusItem> allMeals = new ArrayList<>();
    private FirebaseFirestore db;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_status_history);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        initViews();
        fetchMealHistory();
    }

    // 1. ADD: Handle Physical Back Button (Backward Animation)
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void initViews() {
        // --- 1. SETUP HEADER ---
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);

        // IMPORTANT: Use the ID from the included header layout
        btnBack = findViewById(R.id.btnHeaderBack);

        // Set Title and Clean UI
        tvHeaderTitle.setText("Meal History");
        tvHeaderSubtitle.setVisibility(View.GONE);
        btnBack.setVisibility(View.VISIBLE);

        // Handle Back Button (Backward Animation)
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // --- 2. SETUP BODY VIEWS ---
        rvMealStatus = findViewById(R.id.rvMealStatus);
        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        pbLoading = findViewById(R.id.pbLoading);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        // Setup RecyclerView & Adapter
        rvMealStatus.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MealStatusAdapter(this, new ArrayList<>());
        rvMealStatus.setAdapter(adapter);

        // Setup Filters
        chipGroupStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipAll) filterList("all");
            else if (checkedId == R.id.chipEaten) filterList("eaten");
            else if (checkedId == R.id.chipMissed) filterList("missed");
        });
    }

    private void fetchMealHistory() {
        if (uid == null) return;
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);

        db.collection("meal_plans")
                .whereEqualTo("userId", uid)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    allMeals.clear();
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                    for (DocumentSnapshot doc : snapshots) {
                        String dateStr = doc.getString("date");
                        Date dateObj = null;
                        try {
                            if (dateStr != null) dateObj = parser.parse(dateStr);
                        } catch (Exception e) { e.printStackTrace(); }

                        processMealType(doc, "breakfast", dateStr, dateObj);
                        processMealType(doc, "lunch", dateStr, dateObj);
                        processMealType(doc, "dinner", dateStr, dateObj);
                        processMealType(doc, "supper", dateStr, dateObj);
                    }

                    Collections.sort(allMeals);

                    filterList("all");
                    pbLoading.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show();
                });
    }

    private void processMealType(DocumentSnapshot doc, String type, String dateStr, Date dateObj) {
        String name = doc.getString(type);
        if (name != null && !name.trim().isEmpty()) {
            Boolean eaten = doc.getBoolean(type + "Eaten");
            boolean isEaten = (eaten != null) ? eaten : false;

            allMeals.add(new MealStatusItem(name, type, dateStr, dateObj, isEaten));
        }
    }

    private void filterList(String filter) {
        List<MealStatusItem> filtered = new ArrayList<>();

        for (MealStatusItem item : allMeals) {
            if (filter.equals("all")) {
                filtered.add(item);
            } else if (filter.equals("eaten") && item.isEaten) {
                filtered.add(item);
            } else if (filter.equals("missed") && !item.isEaten) {
                filtered.add(item);
            }
        }

        adapter.updateList(filtered);

        if (filtered.isEmpty()) {
            rvMealStatus.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvMealStatus.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }
}