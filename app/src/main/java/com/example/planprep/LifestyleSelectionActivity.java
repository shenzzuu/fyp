package com.example.planprep;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView; // Updated Import
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LifestyleSelectionActivity extends AppCompatActivity {

    // Updated type to MaterialCardView to match XML
    private MaterialCardView cardVeryBusy, cardBalanced, cardNotBusy;
    private Button btnNext;
    private FirebaseFirestore db;

    // Values that will be stored in Firestore
    private String selectedLifestyle = "";
    private boolean isFromSettings = false; // Flag to track origin

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lifestyle_selection);

        db = FirebaseFirestore.getInstance();

        // Check if we came from SettingsActivity
        isFromSettings = getIntent().getBooleanExtra("FROM_SETTINGS", false);

        // Updated IDs to match the new naming convention
        cardVeryBusy = findViewById(R.id.cardRush);
        cardBalanced = findViewById(R.id.cardBalanced);
        cardNotBusy = findViewById(R.id.cardCooking);
        btnNext = findViewById(R.id.btnNext);

        // Change button text if updating from settings
        if (isFromSettings) {
            btnNext.setText("Save Changes");
        }

        // 1. Set Click Listeners
        cardVeryBusy.setOnClickListener(v -> updateSelection("VERY BUSY", cardVeryBusy));
        cardBalanced.setOnClickListener(v -> updateSelection("BALANCED", cardBalanced));
        cardNotBusy.setOnClickListener(v -> updateSelection("NOT BUSY", cardNotBusy));

        // 2. Next Button Logic
        btnNext.setOnClickListener(v -> {
            if (selectedLifestyle.isEmpty()) {
                Toast.makeText(this, "Please select a lifestyle", Toast.LENGTH_SHORT).show();
                return;
            }
            savePreferenceAndContinue();
        });
    }

    // 3. ADD: Handle Physical Back Button
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // If from settings, apply backward animation
        if (isFromSettings) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    // Updated parameter type to MaterialCardView
    private void updateSelection(String type, MaterialCardView selectedCard) {
        selectedLifestyle = type;

        // Reset all to default (gray/unselected)
        // Note: Ensure these drawables exist and look good with CardView
        cardVeryBusy.setStrokeColor(getColor(R.color.surface_variant));
        cardVeryBusy.setStrokeWidth(1);

        cardBalanced.setStrokeColor(getColor(R.color.surface_variant));
        cardBalanced.setStrokeWidth(1);

        cardNotBusy.setStrokeColor(getColor(R.color.surface_variant));
        cardNotBusy.setStrokeWidth(1);

        // Set the clicked card to active (green/selected)
        // Using Stroke for Material Card selection is often cleaner than background swapping
        selectedCard.setStrokeColor(getColor(R.color.selection));
        selectedCard.setStrokeWidth(4);
    }

    private void savePreferenceAndContinue() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("lifestyle", selectedLifestyle);

        db.collection("users").document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (isFromSettings) {
                        // Just go back to Settings
                        Toast.makeText(this, "Lifestyle updated!", Toast.LENGTH_SHORT).show();
                        finish();
                        // 4. Backward Animation (Returning to Settings)
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                    } else {
                        // Continue the onboarding flow
                        Intent intent = new Intent(this, AllergySelectionActivity.class);
                        startActivity(intent);
                        finish();
                        // 5. Forward Animation (Proceeding to Allergy Selection)
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Logic for meal filtering based on your new categories.
     * This follows the logic: More busy = fewer ingredients (faster).
     */
    public boolean shouldIncludeMeal(Map<String, Object> mealData) {
        // 1. Safety Checks
        if (mealData == null || !mealData.containsKey("ingredients")) {
            return false;
        }

        // 2. Safe size calculation (Handling Map vs List automatically)
        Object ingredientsObj = mealData.get("ingredients");
        int ingredientCount = 0;

        if (ingredientsObj instanceof Map) {
            ingredientCount = ((Map<?, ?>) ingredientsObj).size();
        } else if (ingredientsObj instanceof java.util.Collection) {
            // Handles List, Set, ArrayList, etc.
            ingredientCount = ((java.util.Collection<?>) ingredientsObj).size();
        }

        // 3. Logic Application
        switch (selectedLifestyle) {
            case "VERY BUSY":
                // Expanded slightly to include 6 items for better variety
                return ingredientCount <= 6;

            case "BALANCED":
                // Adjusted range
                return ingredientCount > 6 && ingredientCount <= 10;

            case "NOT BUSY":
                return ingredientCount > 10;

            default:
                return true;
        }
    }
}