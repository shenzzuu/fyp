package com.example.planprep;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllergySelectionActivity extends AppCompatActivity {

    private ChipGroup chipGroup;
    private LinearLayout btnNoAllergies;
    private ImageView iconCheck;
    private TextView tvNoAllergies;
    private Button btnFinish;
    private FirebaseFirestore db;

    // Default is TRUE, so it starts highlighted
    private boolean isNoAllergiesSelected = true;
    private boolean isFromSettings = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_allergy_selection);

        db = FirebaseFirestore.getInstance();

        isFromSettings = getIntent().getBooleanExtra("FROM_SETTINGS", false);

        chipGroup = findViewById(R.id.chipGroupAllergies);
        btnNoAllergies = findViewById(R.id.btnNoAllergies);
        iconCheck = findViewById(R.id.iconCheck);
        tvNoAllergies = findViewById(R.id.tvNoAllergies);
        btnFinish = findViewById(R.id.btnFinish);

        if (isFromSettings) {
            btnFinish.setText("Save Changes");
            // NOTE: If you are coming from Settings, you might want to load existing allergies here.
            // For now, we are keeping your request to "Default to None".
        }

        // --- 1. FORCE DEFAULT STATE VISUALLY ---
        // This ensures when the screen opens, the green checkmark is ON.
        chipGroup.clearCheck();
        setNoAllergiesState(true);

        // Listener for the "No Allergies" button
        btnNoAllergies.setOnClickListener(v -> {
            setNoAllergiesState(true);
            chipGroup.clearCheck(); // Uncheck all chips if user clicks "None"
        });

        // Listeners for the Chips (Peanuts, Dairy, etc.)
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        // If user clicks a chip, turn OFF "No Allergies"
                        setNoAllergiesState(false);
                    } else if (chipGroup.getCheckedChipIds().isEmpty()) {
                        // If user unchecks the last chip, AUTO-SELECT "No Allergies"
                        setNoAllergiesState(true);
                    }
                });
            }
        }

        btnFinish.setOnClickListener(v -> saveAllergiesAndFinish());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (isFromSettings) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    private void setNoAllergiesState(boolean isSelected) {
        isNoAllergiesSelected = isSelected;
        if (isSelected) {
            btnNoAllergies.setBackgroundResource(R.drawable.bg_pill_green);
            iconCheck.setVisibility(View.VISIBLE);
            tvNoAllergies.setTextColor(0xFFFFFFFF); // White Text
        } else {
            btnNoAllergies.setBackgroundResource(R.drawable.bg_pill_gray);
            iconCheck.setVisibility(View.GONE);
            tvNoAllergies.setTextColor(0xFF1B5E20); // Dark Green Text
        }
    }

    private void saveAllergiesAndFinish() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        Map<String, Object> data = new HashMap<>();

        // --- 2. ROBUST SAVE LOGIC ---
        if (isNoAllergiesSelected || chipGroup.getCheckedChipIds().isEmpty()) {
            data.put("allergies", new ArrayList<>());
        } else {
            List<String> selectedAllergies = new ArrayList<>();

            for (int id : chipGroup.getCheckedChipIds()) {
                Chip chip = findViewById(id);
                String chipText = chip.getText().toString();

                // --- NORMALIZE DATA BEFORE SAVING ---
                // This ensures the database gets clean keys, not UI labels.
                switch (chipText) {
                    case "Gluten/Wheat":
                        selectedAllergies.add("Gluten"); // Simplify to match JSON
                        break;
                    case "Tree Nuts":
                        selectedAllergies.add("Tree Nuts"); // We save the category, not specific nuts
                        break;
                    default:
                        selectedAllergies.add(chipText); // Everything else (Dairy, Soy, MSG, etc.) is fine
                        break;
                }
            }
            data.put("allergies", selectedAllergies);
        }

        if (!isFromSettings) {
            data.put("setupComplete", true);
        }

        db.collection("users").document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (isFromSettings) {
                        Toast.makeText(this, "Allergies updated!", Toast.LENGTH_SHORT).show();
                        finish();
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                    } else {
                        Toast.makeText(this, "Profile Setup Complete!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save data", Toast.LENGTH_SHORT).show());
    }
}