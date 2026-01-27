package com.example.planprep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private ImageView btnBack;
    private LinearLayout btnUpdateLifestyle, btnUpdateAllergies, btnChangePassword, btnDeleteAccount;
    private TextView tvCurrentLifestyle, tvCurrentAllergies;
    private SwitchCompat switchNotifications, switchDarkMode;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String uid;
    private android.content.SharedPreferences sharedPrefs;

    // Permission Request Code
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        uid = mAuth.getUid();

        // 1. Initialize SharedPreferences (Critical for Theme persistence)
        sharedPrefs = getSharedPreferences("AppConfig", MODE_PRIVATE);

        initViews();
        setupListeners(); // Setup listeners first

        // 2. Load Dark Mode State from Local Storage
        // We ignore the system default and trust our own "AppConfig"
        boolean isDark = sharedPrefs.getBoolean("isDarkMode", false);
        switchDarkMode.setChecked(isDark);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload settings every time the user comes back (e.g., from Android Settings)
        loadUserSettings();
    }

    private void initViews() {
        TextView tvTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvSubtitle = findViewById(R.id.tvHeaderSubtitle);
        btnBack = findViewById(R.id.btnHeaderBack);

        tvTitle.setText("Settings");
        tvSubtitle.setVisibility(View.GONE);
        btnBack.setVisibility(View.VISIBLE);

        btnUpdateLifestyle = findViewById(R.id.btnUpdateLifestyle);
        btnUpdateAllergies = findViewById(R.id.btnUpdateAllergies);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        tvCurrentLifestyle = findViewById(R.id.tvCurrentLifestyle);
        tvCurrentAllergies = findViewById(R.id.tvCurrentAllergies);

        switchNotifications = findViewById(R.id.switchNotifications);
        switchDarkMode = findViewById(R.id.switchDarkMode);
    }

    private void loadUserSettings() {
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                // 1. Load Lifestyle & Allergies
                String lifestyle = doc.getString("lifestyle");
                List<String> allergies = (List<String>) doc.get("allergies");

                if (lifestyle != null) tvCurrentLifestyle.setText("Current: " + lifestyle);
                if (allergies != null && !allergies.isEmpty()) {
                    tvCurrentAllergies.setText("Current: " + TextUtils.join(", ", allergies));
                } else {
                    tvCurrentAllergies.setText("Current: None");
                }

                // 2. Load Notification Preference safely
                Boolean notifsEnabled = doc.getBoolean("notificationsEnabled");
                boolean isEnabled = notifsEnabled != null && notifsEnabled;

                // 3. SYNC WITH SYSTEM PERMISSIONS
                // If Firebase says TRUE, but System says FALSE -> Turn it OFF visually
                if (isEnabled && !hasSystemNotificationPermission()) {
                    switchNotifications.setChecked(false);
                    updateFirestoreNotificationState(false); // Sync DB back to reality
                } else {
                    switchNotifications.setChecked(isEnabled);
                }
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        btnUpdateLifestyle.setOnClickListener(v -> {
            Intent intent = new Intent(this, LifestyleSelectionActivity.class);
            intent.putExtra("FROM_SETTINGS", true);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        btnUpdateAllergies.setOnClickListener(v -> {
            Intent intent = new Intent(this, AllergySelectionActivity.class);
            intent.putExtra("FROM_SETTINGS", true);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        btnChangePassword.setOnClickListener(v -> {
            if (isGoogleUser()) {
                Toast.makeText(this, "Google accounts must change passwords via Google Settings", Toast.LENGTH_LONG).show();
            } else {
                showChangePasswordSheet();
            }
        });

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountBottomSheet());

        // --- UPDATED DARK MODE LOGIC (Local + Firestore) ---
        // Use setOnClickListener to avoid infinite loops during initialization
        switchDarkMode.setOnClickListener(v -> {
            boolean isChecked = switchDarkMode.isChecked();

            // 1. Apply Theme Immediately
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isChecked ?
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES :
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

            // 2. Save to Local Storage (So LoginActivity knows next time)
            sharedPrefs.edit().putBoolean("isDarkMode", isChecked).apply();

            // 3. Save to Firestore (For account backup)
            if (uid != null) {
                db.collection("users").document(uid).update("darkMode", isChecked);
            }
        });

        // --- UPDATED NOTIFICATION LOGIC ---
        switchNotifications.setOnClickListener(v -> {
            boolean isChecked = switchNotifications.isChecked();

            if (isChecked) {
                // --- CASE 1: User wants to turn ON ---
                if (!hasSystemNotificationPermission()) {
                    // Permission is missing -> Request it
                    // We DO NOT update Firestore yet; we wait for the result
                    requestNotificationPermissions();
                } else {
                    // Permission exists -> Update DB
                    updateFirestoreNotificationState(true);
                }
            } else {
                // --- CASE 2: User wants to turn OFF ---
                // We act like we turned it off by saving FALSE to the database.
                // (Android does not allow apps to revoke system permissions programmatically)
                updateFirestoreNotificationState(false);
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- HELPER: Update Firestore ---
    private void updateFirestoreNotificationState(boolean isEnabled) {
        if (uid != null) {
            db.collection("users").document(uid)
                    .update("notificationsEnabled", isEnabled)
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update setting", Toast.LENGTH_SHORT).show());
        }
    }

    // --- HELPER: Check System Permissions ---
    private boolean hasSystemNotificationPermission() {
        // Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Older Android versions don't need runtime permission for notifications
    }

    // --- HELPER: Request Permissions ---
    private void requestNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
        } else {
            // For older phones, check Exact Alarm permission (Android 12+)
            checkExactAlarmPermission();
        }
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Please allow Alarms & Reminders", Toast.LENGTH_LONG).show();
            } else {
                updateFirestoreNotificationState(true);
            }
        } else {
            updateFirestoreNotificationState(true);
        }
    }

    // --- HANDLE PERMISSION RESULT (Android 13 Pop-up) ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted!
                updateFirestoreNotificationState(true);
                // Also check for Alarm permission now
                checkExactAlarmPermission();
            } else {
                // Permission Denied!
                Toast.makeText(this, "Permission required for notifications", Toast.LENGTH_SHORT).show();
                switchNotifications.setChecked(false); // Flip switch back to OFF
                updateFirestoreNotificationState(false);
            }
        }
    }

    // ... (Existing Methods: isGoogleUser, showChangePasswordSheet, showDeleteAccountBottomSheet, deleteUserDataAndAccount remain unchanged) ...
    private boolean isGoogleUser() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            for (UserInfo profile : user.getProviderData()) {
                if (profile.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showChangePasswordSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.layout_change_password_bottom_sheet, null);
        sheet.setContentView(v);

        EditText etCurrent = v.findViewById(R.id.etCurrentPassword);
        EditText etNew = v.findViewById(R.id.etNewPassword);
        MaterialButton btnSave = v.findViewById(R.id.btnSavePassword);
        MaterialButton btnCancel = v.findViewById(R.id.btnCancelPassword);

        btnCancel.setOnClickListener(view -> sheet.dismiss());
        btnSave.setOnClickListener(view -> {
            String curr = etCurrent.getText().toString().trim();
            String nPass = etNew.getText().toString().trim();

            if (curr.isEmpty() || nPass.length() < 6) {
                Toast.makeText(this, "Check inputs (min 6 chars)", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && user.getEmail() != null) {
                AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), curr);
                user.reauthenticate(cred).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        user.updatePassword(nPass).addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show();
                            sheet.dismiss();
                        });
                    } else {
                        Toast.makeText(this, "Incorrect old password", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        sheet.show();
    }

    private void showDeleteAccountBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.layout_delete_account_bottom_sheet, null);
        sheet.setContentView(v);

        EditText etPass = v.findViewById(R.id.etConfirmPasswordDelete);
        MaterialButton btnDel = v.findViewById(R.id.btnConfirmDelete);
        MaterialButton btnCan = v.findViewById(R.id.btnCancelDelete);
        TextView tvDesc = v.findViewById(R.id.tvDeleteDescription);

        if (isGoogleUser()) {
            etPass.setVisibility(View.GONE);
            if (tvDesc != null) tvDesc.setText("Confirm to delete your account linked with Google.");
        }

        btnCan.setOnClickListener(view -> sheet.dismiss());
        btnDel.setOnClickListener(view -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) return;

            if (isGoogleUser()) {
                deleteUserDataAndAccount(user);
            } else {
                String pass = etPass.getText().toString().trim();
                if (pass.isEmpty()) {
                    etPass.setError("Password required");
                    return;
                }
                AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), pass);
                user.reauthenticate(cred).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        deleteUserDataAndAccount(user);
                    } else {
                        Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        sheet.show();
    }

    private void deleteUserDataAndAccount(FirebaseUser user) {
        db.collection("users").document(uid).delete().addOnSuccessListener(aVoid ->
                user.delete().addOnCompleteListener(t -> {
                    if (t.isSuccessful()) {
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                    } else {
                        Toast.makeText(this, "Deletion failed. Please re-login and try again.", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}