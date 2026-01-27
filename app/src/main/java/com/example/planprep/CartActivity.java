package com.example.planprep;
import com.example.planprep.BuildConfig;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CartActivity extends AppCompatActivity {

    private RecyclerView rvGroceryList, rvStoresList;
    private GroceryAdapter adapter;
    private BottomNavigationView bottomNav;
    private LinearLayout emptyState, chipsContainer, ingredientsUiGroup, checkAllContainer;
    private EditText etSearch;

    private TextView chipBreakfast, chipLunch, chipDinner, chipSupper;
    private TextView tabIngredients, tabNearbyStores, selectedChip, tvCheckAll;
    private MaterialButton btnAddIngredient;

    private FirebaseFirestore db;
    private String uid, todayDateId;
    private ProgressBar pbLoading;
    private SwipeRefreshLayout swipeRefresh;

    private FusedLocationProviderClient fusedLocationClient;
    private final String APIFY_TOKEN = BuildConfig.APIFY_TOKEN;
    private final OkHttpClient httpClient = new OkHttpClient();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        todayDateId = sdf.format(Calendar.getInstance().getTime());

        initViews();
        setupBottomNav();
        setupFilterLogic();
        setupSearchLogic();
        setupTabToggle();
        setupCheckAllLogic();
        setupAddIngredientLogic();

        fetchPlannedMeals();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_cart);
        }
    }

    private void initViews() {
        rvGroceryList = findViewById(R.id.rvGroceryList);
        rvStoresList = findViewById(R.id.rvStoresList);
        bottomNav = findViewById(R.id.bottomNav);
        emptyState = findViewById(R.id.emptyState);
        etSearch = findViewById(R.id.etSearch);
        chipsContainer = findViewById(R.id.chipsContainer);
        ingredientsUiGroup = findViewById(R.id.ingredientsUiGroup);
        pbLoading = findViewById(R.id.pbLoading);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        checkAllContainer = findViewById(R.id.checkAllContainer);
        tvCheckAll = findViewById(R.id.tvCheckAll);
        btnAddIngredient = findViewById(R.id.btnAddIngredient);

        tabIngredients = findViewById(R.id.tabIngredients);
        tabNearbyStores = findViewById(R.id.tabNearbyStores);

        chipBreakfast = findViewById(R.id.chipBreakfast);
        chipLunch = findViewById(R.id.chipLunch);
        chipDinner = findViewById(R.id.chipDinner);
        chipSupper = findViewById(R.id.chipSupper);

        rvGroceryList.setLayoutManager(new LinearLayoutManager(this));
        rvStoresList.setLayoutManager(new LinearLayoutManager(this));

        swipeRefresh.setColorSchemeColors(Color.parseColor("#1B5E20"));
        swipeRefresh.setOnRefreshListener(this::checkLocationPermissions);

        // 1. Find the Views included in the header
        TextView tvTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvSubtitle = findViewById(R.id.tvHeaderSubtitle);

        // 2. Set the text for THIS screen
        tvTitle.setText("Grocery List");
        tvSubtitle.setText("For Today's Meals");
    }

    private void setupAddIngredientLogic() {
        btnAddIngredient.setOnClickListener(v -> {
            if (selectedChip == null) {
                Toast.makeText(this, "Select a category first", Toast.LENGTH_SHORT).show();
                return;
            }
            showIngredientBottomSheet(null);
        });
    }

    private void showIngredientBottomSheet(Ingredient ingredientToEdit) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_ingredient_bottom_sheet, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvSheetTitle = sheetView.findViewById(R.id.tvSheetTitle);
        EditText etInput = sheetView.findViewById(R.id.etIngredientInput);
        MaterialButton btnSave = sheetView.findViewById(R.id.btnSaveIngredient);

        if (ingredientToEdit != null) {
            tvSheetTitle.setText("Edit Ingredient");
            etInput.setText(ingredientToEdit.getName());
            btnSave.setText("Update Item");
        } else {
            tvSheetTitle.setText("Add Item to " + selectedChip.getText());
            btnSave.setText("Add to List");
        }

        btnSave.setOnClickListener(v -> {
            String name = etInput.getText().toString().trim();
            if (name.isEmpty()) return;

            if (ingredientToEdit == null) {
                String category = selectedChip.getText().toString();
                Ingredient newIng = new Ingredient(name, false, category, true);
                toggleIngredientInFirestore(newIng);
            } else {
                handleEditLogic(ingredientToEdit, name);
            }
            fetchPlannedMeals();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void handleEditLogic(Ingredient ingredient, String newName) {
        if (newName.equals(ingredient.getName())) return;
        String groceryDocId = uid + "_" + todayDateId;
        String oldKey = "ingredients." + ingredient.getFirestoreKey();
        String newKey = "ingredients.CUSTOM_" + ingredient.getCategory() + "_" + newName;

        Map<String, Object> updates = new HashMap<>();
        updates.put(oldKey, FieldValue.delete());

        Map<String, Object> newData = new HashMap<>();
        newData.put("name", newName);
        newData.put("checked", ingredient.isChecked());
        newData.put("category", ingredient.getCategory());
        newData.put("isCustom", true);

        updates.put(newKey, newData); // Add the new name inside the map

        db.collection("grocery_lists").document(groceryDocId).update(updates);
    }

    private void showDeleteBottomSheet(Ingredient ingredient) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_delete_bottom_sheet, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvDeleteTitle);
        MaterialButton btnDelete = sheetView.findViewById(R.id.btnConfirmDelete);
        MaterialButton btnCancel = sheetView.findViewById(R.id.btnCancelDelete);

        tvTitle.setText("Remove '" + ingredient.getName() + "'?");

        btnDelete.setOnClickListener(v -> {
            String groceryDocId = uid + "_" + todayDateId;
            String key = "ingredients." + ingredient.getFirestoreKey();

            Map<String, Object> updates = new HashMap<>();
            updates.put(key, FieldValue.delete()); // This removes the field from Firestore

            db.collection("grocery_lists").document(groceryDocId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        fetchPlannedMeals(); // Refresh local list
                        bottomSheetDialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show();
                    });
        });

        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();
    }

    private void setupCheckAllLogic() {
        tvCheckAll.setOnClickListener(v -> {
            if (adapter != null && adapter.getItemCount() > 0) {
                boolean currentFirstItemChecked = adapter.getDisplayedList().get(0).isChecked();
                boolean newState = adapter.checkAllDisplayed(!currentFirstItemChecked);
                tvCheckAll.setText(newState ? "Uncheck All" : "Check All");
            }
        });
    }

    private void setupTabToggle() {
        tabIngredients.setOnClickListener(v -> {
            updateTabUI(tabIngredients, tabNearbyStores);
            ingredientsUiGroup.setVisibility(View.VISIBLE);
            rvGroceryList.setVisibility(View.VISIBLE);
            checkAllContainer.setVisibility(View.VISIBLE);
            swipeRefresh.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
        });

        tabNearbyStores.setOnClickListener(v -> {
            updateTabUI(tabNearbyStores, tabIngredients);
            ingredientsUiGroup.setVisibility(View.GONE);
            rvGroceryList.setVisibility(View.GONE);
            checkAllContainer.setVisibility(View.GONE);
            swipeRefresh.setVisibility(View.VISIBLE);
            checkLocationPermissions();
        });
    }

    private void updateTabUI(TextView active, TextView inactive) {
        active.setBackgroundResource(R.drawable.bg_green_rounded);
        active.setTextColor(Color.WHITE);
        active.setTypeface(null, Typeface.BOLD);
        inactive.setBackground(null);
        inactive.setTextColor(Color.parseColor("#757575"));
        inactive.setTypeface(null, Typeface.NORMAL);
    }

    private void checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            swipeRefresh.setRefreshing(false);
        } else {
            getLastLocationAndFetchStores();
        }
    }

    private void getLastLocationAndFetchStores() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    fetchNearbyStores(location.getLatitude(), location.getLongitude());
                } else {
                    fetchNearbyStores(6.4449, 100.2798);
                }
            });
        }
    }

    private void fetchNearbyStores(double userLat, double userLng) {
        runOnUiThread(() -> {
            if (!swipeRefresh.isRefreshing()) pbLoading.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        });
        String url = "https://api.apify.com/v2/acts/compass~crawler-google-places/runs/last/dataset/items?token=" + APIFY_TOKEN;
        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(CartActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<Store> fetchedStores = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONArray array = new JSONArray(response.body().string());
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            String title = obj.optString("title", "Unknown Store");
                            String address = obj.optString("address", "No Address Provided");
                            String storeUrl = obj.optString("url", "");
                            JSONObject locationObj = obj.optJSONObject("location");
                            double distVal = Double.MAX_VALUE;
                            String distTxt = "Distance N/A";
                            if (locationObj != null) {
                                double sLat = locationObj.optDouble("lat");
                                double sLng = locationObj.optDouble("lng");
                                float[] results = new float[1];
                                Location.distanceBetween(userLat, userLng, sLat, sLng, results);
                                distVal = results[0] / 1000.0;
                                distTxt = String.format(Locale.getDefault(), "%.1f km", distVal);
                            }
                            fetchedStores.add(new Store(title, address, storeUrl, distTxt, distVal));
                        }
                        Collections.sort(fetchedStores, (s1, s2) -> Double.compare(s1.getDistanceVal(), s2.getDistanceVal()));
                    } catch (Exception e) { Log.e("API_ERROR", "Parsing error", e); }
                }
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (fetchedStores.isEmpty()) emptyState.setVisibility(View.VISIBLE);
                    else {
                        emptyState.setVisibility(View.GONE);
                        rvStoresList.setAdapter(new StoreAdapter(fetchedStores));
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocationAndFetchStores();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSearchLogic() {
        etSearch.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int clearIcon = (s.length() > 0) ? android.R.drawable.ic_menu_close_clear_cancel : 0;
                etSearch.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, clearIcon, 0);
                if (adapter != null) {
                    adapter.filterBySearch(s.toString());
                    updateEmptyState(s.toString());
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                if (etSearch.getCompoundDrawables()[2] != null) {
                    if (event.getRawX() >= (etSearch.getRight() - etSearch.getCompoundDrawables()[2].getBounds().width() - 50)) {
                        etSearch.setText("");
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void updateEmptyState(String query) {
        if (adapter.getItemCount() == 0) {
            rvGroceryList.setVisibility(View.GONE);
            checkAllContainer.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            TextView tvEmpty = emptyState.findViewById(R.id.tvEmptyText);
            if (tvEmpty != null) tvEmpty.setText("No results for '" + query + "'");
        } else {
            rvGroceryList.setVisibility(View.VISIBLE);
            checkAllContainer.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    private void fetchPlannedMeals() {
        if (uid == null) return;
        String docId = uid + "_" + todayDateId;
        db.collection("meal_plans").document(docId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        processPlanAndJson(documentSnapshot.getData());
                        emptyState.setVisibility(View.GONE);
                        rvGroceryList.setVisibility(View.VISIBLE);
                    } else {
                        processPlanAndJson(new HashMap<>());
                    }
                });
    }

    private void processPlanAndJson(Map<String, Object> planData) {
        List<Ingredient> masterList = new ArrayList<>();
        JSONObject rootJson = loadJSONFromAsset();

        String[] categories = {"breakfast", "lunch", "dinner", "supper"};
        for (String cat : categories) {
            String mealName = (String) planData.get(cat);
            if (mealName != null && !mealName.isEmpty() && rootJson != null) {
                masterList.addAll(extractIngredients(rootJson, cat, mealName));
            }
        }
        syncWithGroceryStatus(masterList);
    }

    private void syncWithGroceryStatus(List<Ingredient> localList) {
        String groceryDocId = uid + "_" + todayDateId;
        DocumentReference groceryRef = db.collection("grocery_lists").document(groceryDocId);

        groceryRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                // This is the "ingredients" map we saved in AddFoodActivity
                Map<String, Object> ingredientsMap = (Map<String, Object>) snapshot.get("ingredients");

                if (ingredientsMap != null) {
                    // Check status for JSON-based ingredients
                    for (Ingredient ing : localList) {
                        String key = ing.getFirestoreKey(); // matches "category_name"
                        if (ingredientsMap.containsKey(key)) {
                            Map<String, Object> data = (Map<String, Object>) ingredientsMap.get(key);
                            if (data != null && data.containsKey("checked")) {
                                ing.setChecked((Boolean) data.get("checked"));
                            }
                        }
                    }

                    // Add CUSTOM items that aren't in the localList
                    for (Map.Entry<String, Object> entry : ingredientsMap.entrySet()) {
                        if (entry.getKey().startsWith("CUSTOM_")) {
                            Map<String, Object> data = (Map<String, Object>) entry.getValue();
                            String name = (String) data.get("name");
                            String cat = (String) data.get("category");
                            boolean checked = (Boolean) data.get("checked");

                            // Avoid adding duplicates if already in list
                            boolean exists = false;
                            for(Ingredient i : localList) {
                                if(i.getFirestoreKey().equals(entry.getKey())) { exists = true; break; }
                            }
                            if(!exists) localList.add(new Ingredient(name, checked, cat, true));
                        }
                    }
                }
            }

            Collections.sort(localList, (i1, i2) -> i1.getName().compareToIgnoreCase(i2.getName()));

            setupAdapter(localList);
        });
    }

    private void setupAdapter(List<Ingredient> list) {
        adapter = new GroceryAdapter(list);
        adapter.setOnItemClickListener(new GroceryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Ingredient ingredient) {
                toggleIngredientInFirestore(ingredient);
            }

            @Override
            public void onEditClick(Ingredient ingredient) {
                showIngredientBottomSheet(ingredient);
            }

            @Override
            public void onDeleteClick(Ingredient ingredient) {
                showDeleteBottomSheet(ingredient);
            }
        });
        rvGroceryList.setAdapter(adapter);
        if (selectedChip != null) filterByChip(selectedChip, selectedChip.getText().toString());
        else filterByChip(chipBreakfast, "Breakfast");
    }

    private void toggleIngredientInFirestore(Ingredient ingredient) {
        String groceryDocId = uid + "_" + todayDateId;
        String key = ingredient.getFirestoreKey();

        // We must nest the update inside the "ingredients" map to avoid overwriting the whole doc
        Map<String, Object> ingData = new HashMap<>();
        ingData.put("name", ingredient.getName());
        ingData.put("checked", ingredient.isChecked());
        ingData.put("category", ingredient.getCategory());
        ingData.put("isCustom", ingredient.isCustom());

        Map<String, Object> update = new HashMap<>();
        update.put("ingredients." + key, ingData); // Dot notation updates specific map key

        db.collection("grocery_lists").document(groceryDocId)
                .update(update)
                .addOnFailureListener(e -> {
                    // If document doesn't exist yet, use set with merge
                    Map<String, Object> initial = new HashMap<>();
                    Map<String, Object> innerMap = new HashMap<>();
                    innerMap.put(key, ingData);
                    initial.put("ingredients", innerMap);
                    db.collection("grocery_lists").document(groceryDocId).set(initial, SetOptions.merge());
                });
    }

    private JSONObject loadJSONFromAsset() {
        try {
            InputStream is = getAssets().open("ingredients.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) { return null; }
    }

    private List<Ingredient> extractIngredients(JSONObject root, String catKey, String mealName) {
        List<Ingredient> list = new ArrayList<>();
        try {
            if (root.has(catKey)) {
                JSONObject catObj = root.getJSONObject(catKey);
                if (catObj.has(mealName)) {
                    JSONArray arr = catObj.getJSONObject(mealName).getJSONArray("ingredients");
                    // IMPORTANT: Keep category lowercase to match the "mealType" from AddFoodActivity
                    for (int i = 0; i < arr.length(); i++) {
                        list.add(new Ingredient(arr.getString(i), false, catKey, false));
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void setupFilterLogic() {
        chipBreakfast.setOnClickListener(v -> filterByChip(chipBreakfast, "Breakfast"));
        chipLunch.setOnClickListener(v -> filterByChip(chipLunch, "Lunch"));
        chipDinner.setOnClickListener(v -> filterByChip(chipDinner, "Dinner"));
        chipSupper.setOnClickListener(v -> filterByChip(chipSupper, "Supper"));
    }

    private void filterByChip(TextView chip, String category) {
        Context context = chip.getContext(); // Get context helper

        // 1. Reset the previously selected chip (make it inactive)
        if (selectedChip != null) {
            // Reset to the generic rounded shape
            selectedChip.setBackgroundResource(R.drawable.bg_white_rounded);
            // Apply the "surface_color" tint (handles dark/light mode background)
            selectedChip.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.surface_color));
            // Set text to secondary color
            selectedChip.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            selectedChip.setTypeface(null, Typeface.NORMAL);
        }

        // 2. Set the new chip to active
        chip.setBackgroundResource(R.drawable.bg_green_rounded);
        // Remove any tint so the pure green drawable shows
        chip.setBackgroundTintList(null);
        chip.setTextColor(Color.WHITE);
        chip.setTypeface(null, Typeface.BOLD);

        selectedChip = chip;

        // 3. Filter Logic (Unchanged)
        if (adapter != null) {
            adapter.filterByCategory(category);
            if (adapter.getItemCount() == 0) {
                rvGroceryList.setVisibility(View.GONE);
                emptyState.setVisibility(View.VISIBLE);
                TextView tvEmpty = emptyState.findViewById(R.id.tvEmptyText);
                if (tvEmpty != null) tvEmpty.setText("No ingredients for " + category);
            } else {
                rvGroceryList.setVisibility(View.VISIBLE);
                emptyState.setVisibility(View.GONE);
                if (tvCheckAll != null && adapter.getDisplayedList().size() > 0) {
                    boolean firstChecked = adapter.getDisplayedList().get(0).isChecked();
                    tvCheckAll.setText(firstChecked ? "Uncheck All" : "Check All");
                }
            }
        }
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_cart) return true;

            // BACKWARD -> Home or Meal Plan
            if (id == R.id.nav_home || id == R.id.nav_meal_plan) {
                if (id == R.id.nav_meal_plan) {
                    startActivity(new Intent(this, MealPlanActivity.class));
                }
                // If Home, we just finish(). If MealPlan, we started it above, then finish().
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                return true;
            }

            // FORWARD -> Profile
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, UserProfileActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                finish();
                return true;
            }
            return true;
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}