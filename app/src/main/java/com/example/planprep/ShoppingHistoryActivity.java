package com.example.planprep;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShoppingHistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private ProgressBar pbHistory;
    private LinearLayout emptyStateLayout;
    private ImageView btnBack;
    private ChipGroup chipGroupHistory;

    private FirebaseFirestore db;
    private String uid;

    private List<HistoryRecord> fullList = new ArrayList<>();
    private List<HistoryRecord> displayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grocery_history);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        initViews();
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
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);

        // IMPORTANT: Use the new ID from the included header
        ImageView btnBack = findViewById(R.id.btnHeaderBack);

        // Set Header Text & Clean UI
        tvHeaderTitle.setText("Shopping History");
        tvHeaderSubtitle.setVisibility(View.GONE);
        btnBack.setVisibility(View.VISIBLE);

        // Handle Back Button (Backward Animation)
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // --- 2. SETUP BODY VIEWS ---
        rvHistory = findViewById(R.id.rvHistory);
        pbHistory = findViewById(R.id.pbHistory);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        chipGroupHistory = findViewById(R.id.chipGroupHistory);

        // Setup RecyclerView
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(displayList);
        rvHistory.setAdapter(adapter);

        // Chip Filtering Logic
        chipGroupHistory.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipCompleted) {
                filterList("Completed");
            } else if (checkedId == R.id.chipIncomplete) {
                filterList("Incomplete");
            } else {
                filterList("All"); // Default
            }
        });
    }

    private void fetchHistory() {
        if (uid == null) return;
        pbHistory.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);

        // NOTE: This query requires a Composite Index in Firestore!
        // (Collection: grocery_lists) -> (Fields: userId Ascending, date Descending)
        db.collection("grocery_lists")
                .whereEqualTo("userId", uid)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    fullList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        try {
                            String date = doc.getString("date");
                            // Safely cast the ingredients map
                            Map<String, Object> ingredientsMap = (Map<String, Object>) doc.get("ingredients");

                            if (ingredientsMap != null && date != null) {
                                List<Ingredient> dayIngredients = new ArrayList<>();
                                int checkedCount = 0;

                                for (Map.Entry<String, Object> entry : ingredientsMap.entrySet()) {
                                    // The value is another Map (the ingredient object)
                                    Map<String, Object> data = (Map<String, Object>) entry.getValue();

                                    if (data != null) {
                                        String name = (String) data.get("name");
                                        Boolean checked = (Boolean) data.get("checked");
                                        String rawCategory = (String) data.get("category");

                                        if (checked == null) checked = false;
                                        if (checked) checkedCount++;

                                        // Fix category formatting ("breakfast" -> "Breakfast")
                                        String category = (rawCategory != null && !rawCategory.isEmpty())
                                                ? capitalize(rawCategory)
                                                : "Others";

                                        Ingredient ing = new Ingredient(name, checked, category, false);
                                        dayIngredients.add(ing);
                                    }
                                }
                                fullList.add(new HistoryRecord(date, dayIngredients, checkedCount));
                            }
                        } catch (Exception e) {
                            android.util.Log.e("HistoryParse", "Error parsing document: " + doc.getId(), e);
                        }
                    }
                    filterList("All");
                    pbHistory.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    pbHistory.setVisibility(View.GONE);
                    // CHECK LOGCAT FOR THE LINK TO CREATE INDEX
                    android.util.Log.e("FirestoreError", "Query Failed: " + e.getMessage());
                    Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // Helper to make "breakfast" look like "Breakfast"
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private void filterList(String filterType) {
        displayList.clear();
        if (filterType.equals("All")) {
            displayList.addAll(fullList);
        } else if (filterType.equals("Completed")) {
            for (HistoryRecord rec : fullList) {
                if (rec.isCompleted()) displayList.add(rec);
            }
        } else if (filterType.equals("Incomplete")) {
            for (HistoryRecord rec : fullList) {
                if (!rec.isCompleted()) displayList.add(rec);
            }
        }

        adapter.notifyDataSetChanged();

        if (displayList.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    // --- MODEL CLASS ---
    public static class HistoryRecord {
        String date;
        List<Ingredient> ingredients;
        int checkedCount;
        boolean isExpanded = false;

        public HistoryRecord(String date, List<Ingredient> ingredients, int checkedCount) {
            this.date = date;
            this.ingredients = ingredients;
            this.checkedCount = checkedCount;
        }

        public boolean isCompleted() {
            return ingredients.size() > 0 && checkedCount == ingredients.size();
        }
    }

    // --- ADAPTER ---
    public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<HistoryRecord> list;

        public HistoryAdapter(List<HistoryRecord> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grocery_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryRecord record = list.get(position);

            holder.tvDate.setText(record.date);
            holder.tvItemCount.setText(record.ingredients.size() + " items");

            // Expand/Collapse Logic
            if (record.isExpanded) {
                holder.layoutIngredients.setVisibility(View.VISIBLE);
                holder.ivExpandArrow.setRotation(180); // Rotate Up

                holder.layoutIngredients.removeAllViews(); // Clean up before adding

                // Group ingredients by Category
                Map<String, List<Ingredient>> grouped = new HashMap<>();
                for (Ingredient ing : record.ingredients) {
                    if (!grouped.containsKey(ing.getCategory())) {
                        grouped.put(ing.getCategory(), new ArrayList<>());
                    }
                    grouped.get(ing.getCategory()).add(ing);
                }

                // Preferred sort order
                String[] order = {"Breakfast", "Lunch", "Dinner", "Supper", "Others"};

                for (String cat : order) {
                    if (grouped.containsKey(cat)) {
                        addCategorySection(holder, cat, grouped.get(cat));
                        grouped.remove(cat);
                    }
                }
                // Add remaining categories
                for (String cat : grouped.keySet()) {
                    addCategorySection(holder, cat, grouped.get(cat));
                }

            } else {
                holder.layoutIngredients.setVisibility(View.GONE);
                holder.ivExpandArrow.setRotation(0); // Rotate Down
            }

            // Click listener for EXPANDING only
            holder.layoutHeader.setOnClickListener(v -> {
                record.isExpanded = !record.isExpanded;
                notifyItemChanged(position);
            });
        }

        /**
         * Helper to dynamically add views.
         * VIEW ONLY: ensures items are not clickable.
         */
        private void addCategorySection(ViewHolder holder, String categoryTitle, List<Ingredient> items) {
            Context context = holder.itemView.getContext();

            // 1. Inflate Header
            View headerView = LayoutInflater.from(context)
                    .inflate(R.layout.item_grocery_header, holder.layoutIngredients, false);
            TextView tvHeader = headerView.findViewById(R.id.tvHeaderTitle);
            tvHeader.setText(categoryTitle);

            // Optional: Ensure header follows theme (remove if handled in XML layout)
            // tvHeader.setTextColor(ContextCompat.getColor(context, R.color.text_primary));

            holder.layoutIngredients.addView(headerView);

            // Pre-resolve colors to avoid calling getContext() inside the loop repeatedly
            int colorTextPrimary = ContextCompat.getColor(context, R.color.text_primary);
            int colorTextSecondary = ContextCompat.getColor(context, R.color.text_secondary);
            int colorIconAction = ContextCompat.getColor(context, R.color.icon_action);

            // 2. Inflate Items
            for (Ingredient ing : items) {
                View row = LayoutInflater.from(context)
                        .inflate(R.layout.item_grocery_row_simple, holder.layoutIngredients, false);

                // IMPORTANT: Disable clicks/focus to ensure VIEW ONLY feel
                row.setClickable(false);
                row.setFocusable(false);

                TextView tvName = row.findViewById(R.id.tvName);
                TextView tvCategory = row.findViewById(R.id.tvCategory);
                ImageView ivCheck = row.findViewById(R.id.ivCheck);

                tvName.setText(ing.getName());
                tvCategory.setText(ing.getCategory());

                // Ensure the secondary text (category) also adapts
                tvCategory.setTextColor(colorTextSecondary);

                if (ing.isChecked()) {
                    ivCheck.setImageResource(R.drawable.checkbox_square_checked);

                    // Apply mapped color (Green in Day / White in Night)
                    ivCheck.setColorFilter(colorIconAction);

                    // Apply mapped color (Grey in Day / Dimmed White in Night)
                    tvName.setTextColor(colorTextSecondary);
                } else {
                    ivCheck.setImageResource(R.drawable.checkbox_square_unchecked);

                    // Apply mapped color (Green in Day / White in Night)
                    ivCheck.setColorFilter(colorIconAction);

                    // Apply mapped color (Black in Day / White in Night)
                    tvName.setTextColor(colorTextPrimary);
                }

                holder.layoutIngredients.addView(row);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvItemCount;
            ImageView ivExpandArrow;
            LinearLayout layoutIngredients, layoutHeader;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvItemCount = itemView.findViewById(R.id.tvItemCount);
                ivExpandArrow = itemView.findViewById(R.id.ivExpandArrow);
                layoutIngredients = itemView.findViewById(R.id.layoutIngredients);
                layoutHeader = itemView.findViewById(R.id.layoutHeader);
            }
        }
    }
}