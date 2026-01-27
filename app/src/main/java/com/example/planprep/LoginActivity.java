package com.example.planprep;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest; // <--- ADDED THIS IMPORT
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private Button btnLoginEmail, btnGoogle;
    private TextView tvSignUpFooter;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private FirebaseFirestore db;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    try {
                        GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data)
                                .getResult(ApiException.class);
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        setLoading(false);
                        Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    setLoading(false); // User cancelled the picker
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. FORCE THEME (Must be before super.onCreate)
        android.content.SharedPreferences prefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("isDarkMode", false);

        // Apply the theme to the App Logic
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);

        // --- FIXED STATUS BAR LOGIC ---
        // Start with base flags (Layout Fullscreen)
        int systemUiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

        if (!isDarkMode) {
            // If Light Mode (White Background) -> We need DARK Icons.
            // This flag tells Android: "The Status Bar is Light, so please make text Black"
            systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        // If Dark Mode -> We do NOT add the flag, so icons remain White (default).

        getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);
        // -----------------------------

        // 2. FORCE WINDOW BACKGROUND COLOR
        if (isDarkMode) {
            getWindow().getDecorView().setBackgroundColor(android.graphics.Color.parseColor("#121212"));
        } else {
            getWindow().getDecorView().setBackgroundColor(android.graphics.Color.WHITE);
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 3. Auto-login check
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            checkUserSetupAndNavigate(currentUser);
            return;
        }

        setContentView(R.layout.activity_login);

        btnLoginEmail = findViewById(R.id.btnLoginEmail);
        btnGoogle = findViewById(R.id.btnGoogle);
        tvSignUpFooter = findViewById(R.id.tvSignUpFooter);
        progressBar = findViewById(R.id.progressBar);

        btnLoginEmail.setOnClickListener(v -> showAuthBottomSheet(true));
        tvSignUpFooter.setOnClickListener(v -> showAuthBottomSheet(false));
        btnGoogle.setOnClickListener(v -> startGoogleSignIn());

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        btnGoogle.setEnabled(!isLoading);
        btnLoginEmail.setEnabled(!isLoading);
    }

    private void startGoogleSignIn() {
        setLoading(true);
        // FORCE ACCOUNT PICKER: Sign out from Google Client before showing picker
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = auth.getCurrentUser();
                saveUserToFirestore(user);
                checkUserSetupAndNavigate(user);
            } else {
                setLoading(false);
                Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAuthBottomSheet(boolean isLoginMode) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_auth, null);
        bottomSheetDialog.setContentView(sheetView);

        // 1. Bind UI Components
        TextView tabLogin = sheetView.findViewById(R.id.tabLogin);
        TextView tabSignup = sheetView.findViewById(R.id.tabSignup);
        TextInputLayout layoutUsername = sheetView.findViewById(R.id.layoutUsername);
        TextInputLayout layoutEmail = sheetView.findViewById(R.id.layoutEmail);
        TextInputEditText etUsername = sheetView.findViewById(R.id.etUsername);
        TextInputEditText etEmail = sheetView.findViewById(R.id.etEmail);
        TextInputEditText etPassword = sheetView.findViewById(R.id.etPassword);
        Button btnSubmit = sheetView.findViewById(R.id.btnSubmit);

        // 2. UI Update Logic
        Runnable updateUI = () -> {
            boolean isLogin = (boolean) tabLogin.getTag();
            Context context = btnSubmit.getContext();

            // 1. Fetch Dynamic Colors (These change based on Day/Night mode)
            int colorTextActive = ContextCompat.getColor(context, R.color.text_primary);   // Black (Day) / White (Night)
            int colorTextInactive = ContextCompat.getColor(context, R.color.text_secondary); // Grey
            int colorBgSelected = ContextCompat.getColor(context, R.color.surface_color);  // White (Day) / Dark (Night)

            if (isLogin) {
                // --- Login Mode Active ---

                // Tab Styles
                tabLogin.setBackgroundResource(R.drawable.bg_tab_selected);
                tabLogin.setBackgroundTintList(ColorStateList.valueOf(colorBgSelected)); // Adapts background
                tabLogin.setTextColor(colorTextActive); // White in Dark, Black in Day

                tabSignup.setBackgroundResource(0);
                tabSignup.setTextColor(colorTextInactive);

                // Content
                layoutUsername.setVisibility(View.GONE);
                layoutEmail.setHint("Email or Username");
                btnSubmit.setText("Log In");

            } else {
                // --- Sign Up Mode Active ---

                // Tab Styles
                tabLogin.setBackgroundResource(0);
                tabLogin.setTextColor(colorTextInactive);

                tabSignup.setBackgroundResource(R.drawable.bg_tab_selected);
                tabSignup.setBackgroundTintList(ColorStateList.valueOf(colorBgSelected)); // Adapts background
                tabSignup.setTextColor(colorTextActive); // White in Dark, Black in Day

                // Content
                layoutUsername.setVisibility(View.VISIBLE);
                layoutEmail.setHint("Email");
                btnSubmit.setText("Sign Up");
            }
        };

        // 3. Set Initial State
        tabLogin.setTag(isLoginMode);
        updateUI.run();

        tabLogin.setOnClickListener(v -> {
            if (!(boolean) tabLogin.getTag()) {
                tabLogin.setTag(true);
                updateUI.run();
            }
        });

        tabSignup.setOnClickListener(v -> {
            if ((boolean) tabLogin.getTag()) {
                tabLogin.setTag(false);
                updateUI.run();
            }
        });

        // 4. Submit Button Logic
        btnSubmit.setOnClickListener(v -> {
            String inputEmail = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            boolean isLogin = (boolean) tabLogin.getTag();

            if (TextUtils.isEmpty(inputEmail) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            bottomSheetDialog.dismiss();
            setLoading(true);

            if (isLogin) {
                // LOGIN FLOW
                if (inputEmail.contains("@")) {
                    performLogin(inputEmail, password);
                } else {
                    db.collection("users").whereEqualTo("username", inputEmail).get()
                            .addOnSuccessListener(snapshots -> {
                                if (!snapshots.isEmpty()) {
                                    String emailFromDb = snapshots.getDocuments().get(0).getString("email");
                                    performLogin(emailFromDb, password);
                                } else {
                                    setLoading(false);
                                    Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            } else {
                // SIGNUP FLOW
                auth.createUserWithEmailAndPassword(inputEmail, password)
                        .addOnSuccessListener(authResult -> {
                            FirebaseUser fUser = authResult.getUser();

                            // --- CRITICAL UPDATE: SET DISPLAY NAME IN AUTH ---
                            if (fUser != null) {
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(username)
                                        .build();
                                fUser.updateProfile(profileUpdates);
                            }
                            // -------------------------------------------------

                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("uid", fUser.getUid());
                            userMap.put("email", inputEmail);
                            userMap.put("displayName", username);
                            userMap.put("username", username);
                            userMap.put("lifestyle", "BALANCED");
                            userMap.put("setupComplete", false);
                            userMap.put("lastLogin", System.currentTimeMillis());
                            userMap.put("allergies", new java.util.ArrayList<String>());

                            db.collection("users").document(fUser.getUid())
                                    .set(userMap, SetOptions.merge())
                                    .addOnSuccessListener(aVoid -> {
                                        // DO NOT Sign Out here. Let them proceed directly.
                                        // auth.signOut(); <--- Removed this so they stay logged in
                                        setLoading(false);

                                        // Navigate directly to Setup
                                        Intent intent = new Intent(this, LifestyleSelectionActivity.class);
                                        startActivity(intent);
                                        finish();
                                    });
                        })
                        .addOnFailureListener(e -> {
                            setLoading(false);
                            Toast.makeText(this, "Signup Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }
        });

        bottomSheetDialog.show();
    }

    private void performLogin(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    db.collection("users").document(authResult.getUser().getUid())
                            .update("lastLogin", System.currentTimeMillis());
                    checkUserSetupAndNavigate(authResult.getUser());
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Login Failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void checkUserSetupAndNavigate(FirebaseUser user) {
        if (user == null) {
            setLoading(false);
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    Intent intent;
                    if (doc.exists() && doc.getBoolean("setupComplete") != null && doc.getBoolean("setupComplete")) {
                        intent = new Intent(this, MainActivity.class);
                    } else {
                        intent = new Intent(this, LifestyleSelectionActivity.class);
                    }
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> setLoading(false));
    }

    private void saveUserToFirestore(FirebaseUser user) {
        if (user == null) return;
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            Map<String, Object> userData = new HashMap<>();
            userData.put("uid", user.getUid());
            userData.put("email", user.getEmail());
            userData.put("lastLogin", System.currentTimeMillis());

            if (!doc.exists()) {
                userData.put("displayName", user.getDisplayName());
                userData.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
                userData.put("lifestyle", "BALANCED");
                userData.put("setupComplete", false);
                userData.put("allergies", new java.util.ArrayList<String>());
            }
            db.collection("users").document(user.getUid()).set(userData, SetOptions.merge());
        });
    }
}