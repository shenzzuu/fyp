package com.example.planprep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

public class UserProfileActivity extends AppCompatActivity {

    private static final String IMGBB_API_KEY = "5ede3a4c752366497bce48ed19cae856";

    private TextView tvUsername, tvUserEmail;
    private ImageView ivProfilePic;
    private ProgressBar pbImageUpload;
    private BottomNavigationView bottomNavigationView;
    private FirebaseFirestore db;
    private String uid;
    private Uri cropResultUri;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openGallery();
                else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (cropResultUri != null) {
                        uploadImageToImgBB(cropResultUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        android.os.StrictMode.VmPolicy.Builder builder = new android.os.StrictMode.VmPolicy.Builder();
        android.os.StrictMode.setVmPolicy(builder.build());

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        tvUsername = findViewById(R.id.tvUsername);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        ivProfilePic = findViewById(R.id.ivProfilePic);
        pbImageUpload = findViewById(R.id.pbImageUpload);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        TextView tvTitle = findViewById(R.id.tvHeaderTitle);
        TextView tvSubtitle = findViewById(R.id.tvHeaderSubtitle);

        tvTitle.setText("My Profile");
        tvSubtitle.setText("Manage your account");

        loadUserData();
        setupClickListeners();
        setupNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        }
    }

    private void setupNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) return true;

            // BACKWARD -> Any other tab
            if (id == R.id.nav_home) {
                finish();
            } else {
                Intent intent = null;
                if (id == R.id.nav_meal_plan) intent = new Intent(this, MealPlanActivity.class);
                else if (id == R.id.nav_cart) intent = new Intent(this, CartActivity.class);

                if (intent != null) startActivity(intent);
                finish();
            }

            // Apply standard "Back" animation for all exits from Profile
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            return true;
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void setupClickListeners() {
        findViewById(R.id.cardProfilePic).setOnClickListener(v -> checkPermissionAndOpenGallery());
        findViewById(R.id.btnEditName).setOnClickListener(v -> showEditNameBottomSheet());

        // --- 1. My Plan Card ---
        findViewById(R.id.cardMyPlan).setOnClickListener(v -> {
            startActivity(new Intent(this, MealPlanHistoryActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // --- 2. History Card ---
        findViewById(R.id.cardHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, MealStatusHistoryActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // --- 3. Favorites Card ---
        findViewById(R.id.cardFavorites).setOnClickListener(v -> {
            startActivity(new Intent(this, FavoritesActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // --- 4. Grocery Card ---
        findViewById(R.id.cardGrocery).setOnClickListener(v -> {
            startActivity(new Intent(this, ShoppingHistoryActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // 5. Settings (ID changed from cardSettings to btnSettings)
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // 6. Logout (ID changed from cardLogout to btnLogout)
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutBottomSheet());
    }

    // --- HELPER METHODS ---

    private void openGallery() {
        try {
            File tempFile = new File(getExternalCacheDir(), "cropped_image.jpg");
            if (tempFile.getParentFile() != null) tempFile.getParentFile().mkdirs();

            cropResultUri = FileProvider.getUriForFile(this,
                    "com.example.planprep.fileprovider", tempFile);

            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("outputX", 512);
            intent.putExtra("outputY", 512);
            intent.putExtra("scale", true);
            intent.putExtra("scaleUpIfNeeded", true);
            intent.putExtra("return-data", false);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cropResultUri);
            intent.putExtra("outputFormat", "JPEG");

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            java.util.List<android.content.pm.ResolveInfo> resInfoList = getPackageManager()
                    .queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            for (android.content.pm.ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                grantUriPermission(packageName, cropResultUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            galleryLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Setup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImageToImgBB(Uri imageUri) {
        if (pbImageUpload != null) pbImageUpload.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) throw new Exception("Could not open image file");

                File uploadFile = new File(getCacheDir(), "temp_upload_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream out = new FileOutputStream(uploadFile);

                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.close();
                inputStream.close();

                OkHttpClient client = new OkHttpClient();
                RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), uploadFile);

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("image", uploadFile.getName(), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.imgbb.com/1/upload?key=" + IMGBB_API_KEY)
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.body() == null) throw new Exception("Empty response from server");

                    String responseData = response.body().string();
                    JSONObject json = new JSONObject(responseData);

                    if (response.isSuccessful() && json.has("data")) {
                        String url = json.getJSONObject("data").getString("url");
                        if (uploadFile.exists()) uploadFile.delete();

                        runOnUiThread(() -> {
                            if (pbImageUpload != null) pbImageUpload.setVisibility(View.GONE);
                            updatePhotoInFirestore(url);
                        });
                    } else {
                        String errorMsg = json.has("error") ? json.getJSONObject("error").getString("message") : "Unknown API error";
                        throw new Exception(errorMsg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (pbImageUpload != null) pbImageUpload.setVisibility(View.GONE);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadUserData() {
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                tvUsername.setText(doc.getString("displayName"));
                tvUserEmail.setText(doc.getString("email"));
                String photo = doc.getString("photoUrl");
                if (photo != null && !photo.isEmpty()) {
                    Glide.with(this).load(photo).placeholder(R.drawable.profile).circleCrop().into(ivProfilePic);
                }
            }
        });
    }

    private void updateNameInFirestore(String newName) {
        db.collection("users").document(uid).update("displayName", newName)
                .addOnSuccessListener(aVoid -> tvUsername.setText(newName));
    }

    private void updatePhotoInFirestore(String url) {
        db.collection("users").document(uid).update("photoUrl", url)
                .addOnSuccessListener(aVoid -> {
                    Glide.with(this).load(url).circleCrop().into(ivProfilePic);
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
                });
    }

    private void checkPermissionAndOpenGallery() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) openGallery();
        else requestPermissionLauncher.launch(permission);
    }

    private void showEditNameBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_edit_name_bottom_sheet, null);
        bottomSheet.setContentView(view);
        EditText etName = view.findViewById(R.id.etNewName);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveName);
        etName.setText(tvUsername.getText().toString());
        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (!newName.isEmpty()) {
                updateNameInFirestore(newName);
                bottomSheet.dismiss();
            }
        });
        bottomSheet.show();
    }

    private void showLogoutBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_logout_bottom_sheet, null);
        bottomSheet.setContentView(view);
        view.findViewById(R.id.btnConfirmLogout).setOnClickListener(v -> {
            bottomSheet.dismiss();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
        view.findViewById(R.id.btnCancelLogout).setOnClickListener(v -> bottomSheet.dismiss());
        bottomSheet.show();
    }
}