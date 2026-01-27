package com.example.planprep;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MealPlanHistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private ProgressBar pbLoading;
    private ChipGroup chipGroupFilters;
    private ImageView btnBack;

    // Empty State View
    private View layoutEmptyState;

    private FirebaseFirestore db;
    private String uid;
    private List<DocumentSnapshot> allHistoryDocs = new ArrayList<>();

    private final SimpleDateFormat sdfId = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_plan_history);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        initViews();
        setupFilters();
        fetchHistory();
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
        // Access views from the included layout_custom_header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);

        // IMPORTANT: Update this ID to match the reusable header
        btnBack = findViewById(R.id.btnHeaderBack);

        // Set the Title and cleanup UI
        tvHeaderTitle.setText("Plan History");
        tvHeaderSubtitle.setVisibility(View.GONE); // Hide subtitle
        btnBack.setVisibility(View.VISIBLE);

        // --- 2. HANDLE BACK BUTTON ---
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // --- 3. SETUP BODY VIEWS ---
        rvHistory = findViewById(R.id.rvHistory);
        pbLoading = findViewById(R.id.pbLoading);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        // Setup RecyclerView
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(this, new ArrayList<>(), this::onReusePlanClicked);
        rvHistory.setAdapter(adapter);
    }

    private void fetchHistory() {
        if (uid == null) return;
        pbLoading.setVisibility(View.VISIBLE);

        db.collection("meal_plans")
                .whereEqualTo("userId", uid)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allHistoryDocs = queryDocumentSnapshots.getDocuments();
                    // Reset filter to All when refreshing to ensure user sees everything
                    chipGroupFilters.check(R.id.chipAll);
                    filterList("all");
                    pbLoading.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupFilters() {
        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipAll) filterList("all");
            else if (checkedId == R.id.chipThisWeek) filterList("this_week");
            else if (checkedId == R.id.chipLastWeek) filterList("last_week");
            else if (checkedId == R.id.chipCustom) showCustomDatePicker();
        });
    }

    // --- CHANGED: Now uses Single Date Picker (Like Reuse Logic) ---
    private void showCustomDatePicker() {
        // We do NOT use DateValidatorPointForward here because
        // in history you might want to see PAST dates.

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Filter by Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);
            String selectedDate = sdfId.format(calendar.getTime());

            filterByExactDate(selectedDate);
        });

        datePicker.show(getSupportFragmentManager(), "FILTER_PICKER");
    }

    private void filterByExactDate(String targetDate) {
        List<DocumentSnapshot> filtered = new ArrayList<>();
        for (DocumentSnapshot doc : allHistoryDocs) {
            String docDate = doc.getString("date");
            if (docDate != null && docDate.equals(targetDate)) {
                filtered.add(doc);
            }
        }
        adapter.updateList(filtered);
        toggleEmptyState(filtered.isEmpty());
    }

    private void filterList(String type) {
        List<DocumentSnapshot> filtered = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        // --- FIX: RESET TIME TO MIDNIGHT ---
        // This ensures "Today" includes the whole day, starting from 00:00:00
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // -----------------------------------

        Date startDate = null, endDate = null;

        if (type.equals("this_week")) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            startDate = cal.getTime();
            cal.add(Calendar.DAY_OF_WEEK, 6);
            endDate = cal.getTime();
        } else if (type.equals("last_week")) {
            cal.add(Calendar.WEEK_OF_YEAR, -1);
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            startDate = cal.getTime();
            cal.add(Calendar.DAY_OF_WEEK, 6);
            endDate = cal.getTime();
        }

        for (DocumentSnapshot doc : allHistoryDocs) {
            String dateStr = doc.getString("date");
            if (dateStr == null) continue;

            if (type.equals("all")) {
                filtered.add(doc);
            } else {
                try {
                    Date docDate = sdfId.parse(dateStr);
                    if (docDate != null && isWithinRange(docDate, startDate, endDate)) {
                        filtered.add(doc);
                    }
                } catch (ParseException e) { e.printStackTrace(); }
            }
        }

        adapter.updateList(filtered);
        toggleEmptyState(filtered.isEmpty());
    }

    private void toggleEmptyState(boolean isEmpty) {
        if (isEmpty) {
            rvHistory.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvHistory.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private boolean isWithinRange(Date target, Date start, Date end) {
        return target.compareTo(start) >= 0 && target.compareTo(end) <= 0;
    }

    // --- REUSE LOGIC ---

    private void onReusePlanClicked(DocumentSnapshot sourcePlan) {
        // Reuse logic KEEPS constraints (User can only reuse to FUTURE dates)
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date to Reuse Plan")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);
            String targetDateId = sdfId.format(calendar.getTime());

            checkAndReuse(sourcePlan, targetDateId);
        });

        datePicker.show(getSupportFragmentManager(), "REUSE_PICKER");
    }

    private void checkAndReuse(DocumentSnapshot sourcePlan, String targetDateId) {
        String targetDocId = uid + "_" + targetDateId;

        db.collection("meal_plans").document(targetDocId).get()
                .addOnSuccessListener(targetSnap -> {
                    if (targetSnap.exists()) {
                        String b = targetSnap.getString("breakfast");
                        String l = targetSnap.getString("lunch");
                        String d = targetSnap.getString("dinner");
                        String s = targetSnap.getString("supper");

                        boolean hasMeal = (b != null && !b.isEmpty()) ||
                                (l != null && !l.isEmpty()) ||
                                (d != null && !d.isEmpty()) ||
                                (s != null && !s.isEmpty());

                        if (hasMeal) {
                            Toast.makeText(this, "A plan already exists for " + targetDateId, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    executeReuseLogic(sourcePlan, targetDateId);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error checking date availability", Toast.LENGTH_SHORT).show()
                );
    }

    private void executeReuseLogic(DocumentSnapshot sourcePlan, String targetDateId) {
        String targetDocId = uid + "_" + targetDateId;
        Map<String, Object> sourceData = sourcePlan.getData();
        if (sourceData == null) return;

        Map<String, Object> newData = new HashMap<>(sourceData);
        newData.put("date", targetDateId);
        newData.put("breakfastEaten", false);
        newData.put("lunchEaten", false);
        newData.put("dinnerEaten", false);
        newData.put("supperEaten", false);
        newData.remove("ingredients");

        String sourceDateId = sourcePlan.getString("date");
        String sourceGroceryId = uid + "_" + sourceDateId;

        db.collection("grocery_lists").document(sourceGroceryId).get()
                .addOnSuccessListener(grocerySnap -> {
                    Map<String, Object> groceryData = null;
                    if (grocerySnap.exists()) {
                        groceryData = grocerySnap.getData();
                        if (groceryData != null) {
                            groceryData.put("date", targetDateId);
                            Map<String, Object> ings = (Map<String, Object>) groceryData.get("ingredients");
                            if (ings != null) {
                                for (Map.Entry<String, Object> entry : ings.entrySet()) {
                                    Map<String, Object> item = (Map<String, Object>) entry.getValue();
                                    item.put("checked", false);
                                }
                            }
                        }
                    }
                    writeReuseData(targetDocId, newData, groceryData);
                });
    }

    private void writeReuseData(String targetDocId, Map<String, Object> mealData, Map<String, Object> groceryData) {
        db.collection("meal_plans").document(targetDocId)
                .set(mealData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (groceryData != null) {
                        db.collection("grocery_lists").document(targetDocId)
                                .set(groceryData, SetOptions.merge());
                    }
                    Toast.makeText(this, "Plan reused successfully!", Toast.LENGTH_SHORT).show();

                    // --- CHANGED: AUTO REFRESH HERE ---
                    fetchHistory();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to reuse plan", Toast.LENGTH_SHORT).show());
    }
}