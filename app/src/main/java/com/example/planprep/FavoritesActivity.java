package com.example.planprep;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class FavoritesActivity extends AppCompatActivity {

    private RecyclerView rvFavorites;
    private FavoritesAdapter adapter;
    private ProgressBar pbLoading;
    private LinearLayout layoutEmptyState;
    private ImageView btnBack;
    private ChipGroup chipGroup;

    private List<FavoriteItem> fullList = new ArrayList<>();
    private List<FavoriteItem> displayList = new ArrayList<>();

    private FirebaseFirestore db;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        initViews();
        setupAdapter();
        fetchFavorites();
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

        // IMPORTANT: Use the new ID from the included header
        btnBack = findViewById(R.id.btnHeaderBack);

        // Set Title and clean up UI
        tvHeaderTitle.setText("My Favorites");
        tvHeaderSubtitle.setVisibility(View.GONE);
        btnBack.setVisibility(View.VISIBLE);

        // Handle Back Button (Backward Animation)
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // --- 2. SETUP BODY VIEWS ---
        rvFavorites = findViewById(R.id.rvFavorites);
        pbLoading = findViewById(R.id.pbLoading);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        chipGroup = findViewById(R.id.chipGroup);

        rvFavorites.setLayoutManager(new LinearLayoutManager(this));

        // Chip Filter Logic
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == -1) return;
            Chip chip = findViewById(checkedId);
            if (chip != null) {
                filterByChip(chip.getText().toString());
            }
        });
    }

    private void setupAdapter() {
        adapter = new FavoritesAdapter(this, displayList, new FavoritesAdapter.OnItemClickListener() {
            @Override
            public void onDeleteClick(int position, String docId) {
                FavoriteItem item = displayList.get(position);
                showDeleteBottomSheet(item, position, docId);
            }

            @Override
            public void onItemClick(FavoriteItem item) {
                // Optional: Open Details
            }

            @Override
            public void onAddToPlanClick(FavoriteItem item) {
                // 1. Start flow: Pick Date
                showDatePicker(item);
            }
        });
        rvFavorites.setAdapter(adapter);
    }

    // --- STEP 1: PICK DATE ---
    private void showDatePicker(FavoriteItem item) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date for " + item.getName())
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String selectedDateId = sdf.format(new Date(selection));

            // 2. Pick Time
            showTimePicker(item, selectedDateId);
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    // --- STEP 2: PICK TIME ---
    private void showTimePicker(FavoriteItem item, String dateId) {
        boolean isSystem24Hour = DateFormat.is24HourFormat(this);
        int clockFormat = isSystem24Hour ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H;

        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(12)
                .setMinute(0)
                .setTitleText("Select Time")
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            // Format time
            int h = timePicker.getHour();
            int m = timePicker.getMinute();

            // Format based on system preference or force 12h/24h.
            String amPm = (h >= 12) ? "PM" : "AM";
            int h12 = (h > 12) ? h - 12 : h;
            if (h12 == 0) h12 = 12;
            String formattedTime = String.format(Locale.getDefault(), "%02d:%02d %s", h12, m, amPm);

            // 3. Fetch ingredients from JSON (Not Firestore) and Save
            List<Ingredient> ingredients = fetchIngredientsForFood(item.getCategory().toLowerCase(), item.getName());
            finalizeMealAddition(item, dateId, formattedTime, ingredients);
        });

        timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
    }

    // --- NEW: FETCH INGREDIENTS FROM JSON (Copied from AddFoodActivity) ---
    private List<Ingredient> fetchIngredientsForFood(String category, String foodName) {
        List<Ingredient> list = new ArrayList<>();
        try {
            InputStream is = getAssets().open("ingredients.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            JSONObject root = new JSONObject(new String(buffer, StandardCharsets.UTF_8));

            if (root.has(category)) {
                JSONObject categoryObj = root.getJSONObject(category);
                if (categoryObj.has(foodName)) {
                    JSONArray ingArray = categoryObj.getJSONObject(foodName).getJSONArray("ingredients");
                    for (int i = 0; i < ingArray.length(); i++) {
                        // Create Ingredient
                        list.add(new Ingredient(ingArray.getString(i), false, category, false));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- STEP 3: UPDATE MEAL PLAN & GROCERY ---
    private void finalizeMealAddition(FavoriteItem item, String dateId, String time, List<Ingredient> ingredients) {
        if (uid == null) return;

        String mealType = item.getCategory().toLowerCase();
        String mealPlanDocId = uid + "_" + dateId;
        String groceryDocId = uid + "_" + dateId;

        // 1. Prepare Meal Plan Data
        Map<String, Object> mealUpdates = new HashMap<>();
        mealUpdates.put("userId", uid);
        mealUpdates.put("date", dateId);
        mealUpdates.put(mealType, item.getName());
        mealUpdates.put(mealType + "Time", time);
        mealUpdates.put(mealType + "Image", item.getImage());
        mealUpdates.put(mealType + "Eaten", false);
        mealUpdates.put(mealType + "Missed", false);

        // 2. Prepare Grocery List Data
        Map<String, Object> ingredientMap = new HashMap<>();

        if (ingredients != null) {
            for (Ingredient ing : ingredients) {
                // Ensure category is set
                String cat = ing.getCategory();
                if(cat == null) cat = mealType;

                Map<String, Object> ingData = new HashMap<>();
                ingData.put("name", ing.getName());
                ingData.put("checked", false);
                ingData.put("category", cat);
                ingData.put("isCustom", false);

                // Use the same key logic as AddFoodActivity
                ingredientMap.put(ing.getFirestoreKey(), ingData);
            }
        }

        // 3. Write to meal_plans
        db.collection("meal_plans").document(mealPlanDocId)
                .set(mealUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {

                    // 4. Write ingredients to grocery_lists
                    Map<String, Object> groceryData = new HashMap<>();
                    groceryData.put("ingredients", ingredientMap);
                    groceryData.put("userId", uid);
                    groceryData.put("date", dateId);

                    db.collection("grocery_lists").document(groceryDocId)
                            .set(groceryData, SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> {
                                Toast.makeText(this, "Added to " + item.getCategory() + " on " + dateId, Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Added to plan, but failed to update Grocery List", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to add to meal plan", Toast.LENGTH_SHORT).show();
                });
    }

    // --- DELETE LOGIC ---
    private void showDeleteBottomSheet(FavoriteItem item, int position, String docId) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        // Ensure this layout file exists in your res/layout folder
        View sheetView = getLayoutInflater().inflate(R.layout.layout_delete_bottom_sheet, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvDeleteTitle);
        MaterialButton btnDelete = sheetView.findViewById(R.id.btnConfirmDelete);
        MaterialButton btnCancel = sheetView.findViewById(R.id.btnCancelDelete);

        tvTitle.setText("Remove '" + item.getName() + "'?");

        btnDelete.setOnClickListener(v -> {
            db.collection("favorites").document(docId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        FavoriteItem itemToRemove = displayList.get(position);
                        displayList.remove(position);
                        fullList.remove(itemToRemove);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, displayList.size());
                        checkEmptyState();
                        Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
                        bottomSheetDialog.dismiss();
                    });
        });

        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();
    }

    private void fetchFavorites() {
        if (uid == null) return;
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);

        db.collection("favorites").whereEqualTo("userId", uid).get()
                .addOnSuccessListener(snapshots -> {
                    fullList.clear();
                    if (!snapshots.isEmpty()) {
                        for (DocumentSnapshot doc : snapshots) {
                            String name = doc.getString("mealName");
                            String image = doc.getString("imageUrl");
                            String category = doc.getString("category");
                            if (category == null) category = "Other";
                            fullList.add(new FavoriteItem(doc.getId(), name, image, category));
                        }
                    }

                    java.util.Collections.sort(fullList, (item1, item2) ->
                            item1.getName().compareToIgnoreCase(item2.getName())
                    );

                    filterByChip("All");
                    pbLoading.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Error loading favorites", Toast.LENGTH_SHORT).show();
                });
    }

    private void filterByChip(String category) {
        displayList.clear();
        if (category.equals("All")) {
            displayList.addAll(fullList);
        } else {
            for (FavoriteItem item : fullList) {
                if (item.getCategory().equalsIgnoreCase(category)) {
                    displayList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (displayList.isEmpty()) {
            rvFavorites.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvFavorites.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }
}