package info.fortheease.rescuenet;

import static com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.CancellationSignal; // Added
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager; // Added
import androidx.credentials.CredentialManagerCallback; // Added
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse; // Added
import androidx.credentials.exceptions.GetCredentialException; // Added
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, ReportAdapter.OnReportClickListener {

    private GoogleMap mMap;
    private IntelligenceService intelligenceService;
    private List<ReportModel> activeReports = new ArrayList<>();
    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean isDataLoadedFromFirestore = false;
    private static final String PREFS_NAME = "RescueNetPrefs";
    private static final String KEY_REPORTS = "activeReports";
    private Location lastKnownLocation;
    private GdacsService gdacsService;

    // Firebase
    private FirebaseFirestore db;
    private CollectionReference reportsRef;
    private CollectionReference usersRef;
    private ListenerRegistration reportsListener;
    private FirebaseAuth mAuth;
    private String currentAnonymousUserId;

    // Auth
    private CredentialManager credentialManager; // Added CredentialManager

    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private boolean areReportsLoaded = false;
    private boolean isMapReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        intelligenceService = new IntelligenceService();

        // Initialize Credential Manager
        credentialManager = CredentialManager.create(this);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Only sign in anonymously if not already signed in (optional logic, keeping your original flow)
        if (mAuth.getCurrentUser() == null) {
            signInAnonymously();
        } else {
            currentAnonymousUserId = mAuth.getCurrentUser().getUid();
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        reportsRef = db.collection("reports");
        usersRef = db.collection("users");

        // Instantiate a Google sign-in request
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Changed to false to allow new accounts to show up
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .build();

        // Create the Credential Manager request
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        MaterialButton gglLogin = findViewById(R.id.btn_google_login);
        gglLogin.setOnClickListener(view -> {
            // Correctly execute the async credential request
            credentialManager.getCredentialAsync(
                    this,
                    request,
                    new CancellationSignal(),
                    ContextCompat.getMainExecutor(this),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            // On success, pass the credential to your handler
                            handleSignIn(result.getCredential());
                        }

                        @Override
                        public void onError(GetCredentialException e) {
                            Log.e("Auth", "Google Sign-In failed", e);
                            Toast.makeText(MainActivity.this, "Sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });

        // Initialize Retrofit for GDACS with standard base URL
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.gdacs.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        gdacsService = retrofit.create(GdacsService.class);

        // Load from SharedPreferences on background thread, then proceed with UI setup
        loadReportsAsync(() -> {
            runOnUiThread(() -> {
                recyclerView = findViewById(R.id.recycler_view);
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                adapter = new ReportAdapter(this, activeReports, this);
                recyclerView.setAdapter(adapter);

                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);
                if (mapFragment != null) {
                    mapFragment.getMapAsync(this);
                }

                ExtendedFloatingActionButton fabReport = findViewById(R.id.fab_report);
                fabReport.setOnClickListener(v -> showReportDialog());

                ExtendedFloatingActionButton fabSos = findViewById(R.id.fab_sos);
                fabSos.setOnClickListener(v -> showSosConfirmation());

                MaterialButton btnSyncFeed = findViewById(R.id.btn_sync_feed);
                btnSyncFeed.setOnClickListener(v -> syncGdacsFeed());

                // Set flag and conditionally refresh map and adapter
                areReportsLoaded = true;
                if (isMapReady) {
                    refreshMap();
                    adapter.notifyDataSetChanged();
                }

                // Check for pending background analysis on startup
                reprocessOfflineReports();

                setupFirestoreListener();

                // Check access permissions after all initializations are done
                checkAccessPermissions();
            });
        });
    }

    private void handleSignIn(Credential credential) {
        // Fix: Split the instanceof check and the variable declaration for Java 8/11 compatibility
        if (credential instanceof CustomCredential &&
                credential.getType().equals(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {

            try {
                // Explicitly cast the credential
                CustomCredential customCredential = (CustomCredential) credential;

                // Create Google ID Token
                Bundle credentialData = customCredential.getData();
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialData);

                // Sign in to Firebase with using the token
                firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
            } catch (Exception e) {
                Log.e("Auth", "Error parsing Google ID Token", e);
            }
        } else {
            Log.w("Auth", "Credential is not of type Google ID!");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("Auth", "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            currentAnonymousUserId = user.getUid();
                            Toast.makeText(MainActivity.this, "Welcome " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                            // Re-check permissions for the new user
                            checkAccessPermissions();
                        }
                    } else {
                        // If sign in fails, display a message to the user
                        Log.w("Auth", "signInWithCredential:failure", task.getException());
                        Toast.makeText(MainActivity.this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkAccessPermissions() {
        // Check if anonymous user ID is available
        if (currentAnonymousUserId == null) {
            return;
        }

        DocumentReference userDocRef = usersRef.document(currentAnonymousUserId);
        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Long mistakeCounter = documentSnapshot.getLong("mistakeCounter");
                if (mistakeCounter != null && mistakeCounter >= 3) {
                    showAccessDeniedDialog();
                }
            }
        }).addOnFailureListener(e -> {
            Log.e("Permissions", "Error fetching user document: ", e);
        });
    }

    private void createUserIfNotFound() {
        if (currentAnonymousUserId == null) {
            return; // Cannot create user if ID is not yet available
        }

        DocumentReference userDocRef = usersRef.document(currentAnonymousUserId);
        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (!task.getResult().exists()) {
                    // User document does not exist, create it with default values
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("anonymousId", currentAnonymousUserId);
                    userData.put("mistakeCounter", 0); // Initialize mistakeCounter to 0

                    userDocRef.set(userData)
                            .addOnSuccessListener(aVoid -> Log.d("UserCreation", "User document created for " + currentAnonymousUserId))
                            .addOnFailureListener(e -> Log.e("UserCreation", "Error creating user document", e));
                } else {
                    // User document already exists, do nothing or log it.
                    Log.d("UserCreation", "User document already exists for " + currentAnonymousUserId);
                }
            }
        });
    }

    private void showAccessDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Access Denied")
                .setMessage("Your access has been denied due to too many mistakes. You will be logged out.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("Auth", "signInAnonymously:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            currentAnonymousUserId = user.getUid();
                            Log.d("Auth", "Anonymous User ID: " + currentAnonymousUserId);
                            // After successful sign-in, check or create the user document
                            createUserIfNotFound();
                            // Also, re-check permissions in case user data was loaded before auth completed.
                            checkAccessPermissions();
                        }
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("Auth", "signInAnonymously:failure", task.getException());
                        Toast.makeText(MainActivity.this, "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupFirestoreListener() {
        reportsListener = reportsRef.orderBy("id", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("Firestore", "Listen failed.", error);
                        return;
                    }

                    if (value != null) {
                        List<ReportModel> firestoreReports = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            ReportModel report = doc.toObject(ReportModel.class);
                            report.setSyncedWithFirestore(true);
                            firestoreReports.add(report);
                        }

                        // Only load mock data if both Firestore and local cache are empty on first successful check
                        if (!isDataLoadedFromFirestore && firestoreReports.isEmpty() && activeReports.isEmpty()) {
                            loadTierA_MockData();
                        }
                        isDataLoadedFromFirestore = true;

                        // Merge with local/GDACS reports and deduplicate
                        mergeAndRefreshReports(firestoreReports);
                    }
                });
    }

    private void mergeAndRefreshReports(List<ReportModel> firestoreReports) {
        // 1. Identify all unique report IDs from Firestore
        Set<String> firestoreIds = new HashSet<>();
        for (ReportModel r : firestoreReports) {
            firestoreIds.add(r.getId());
        }

        // 2. Combine Firestore reports with local reports that haven't synced yet
        List<ReportModel> combined = new ArrayList<>(firestoreReports);
        for (ReportModel local : activeReports) {
            if (!firestoreIds.contains(local.getId())) {
                // If it's a report that was previously synced but now it's gone from Firestore,
                // it means it was deleted on the server. Don't add it back to combined list.
                if (local.isSyncedWithFirestore()) {
                    continue;
                }
                combined.add(local);
            }
        }

        // 3. Deduplicate based on proximity (Merging incidents within 100 meters)
        // This resolves the user's concern about duplicate elements in the BottomSheet
        List<ReportModel> merged = new ArrayList<>();
        for (ReportModel report : combined) {
            boolean wasMerged = false;
            for (ReportModel unique : merged) {
                if (calculateDistance(report, unique) < 100) {
                    // Merge counts
                    unique.setReportCount(unique.getReportCount() + report.getReportCount());
                    // Take the highest severity and most descriptive summary
                    if (report.getSeverityScore() > unique.getSeverityScore()) {
                        unique.setSeverityScore(report.getSeverityScore());
                        unique.setCategory(report.getCategory());
                        unique.setSummary(report.getSummary());
                    }
                    wasMerged = true;
                    break;
                }
            }
            if (!wasMerged) {
                merged.add(report);
            }
        }

        activeReports.clear();
        activeReports.addAll(merged);

        sortReportsByProximity();
        saveReports(); // Cache to SharedPreferences
        refreshMap();
        adapter.notifyDataSetChanged();
    }

    private void reprocessOfflineReports() {
        if (activeReports == null) return;
        for (int i = 0; i < activeReports.size(); i++) {
            ReportModel report = activeReports.get(i);
            if (report == null) continue;

            // Use null-safe comparison for source
            boolean isRescueNet = "RescueNet".equals(report.getSource()) || report.getSource() == null;

            if (isRescueNet && !report.isSyncedWithFirestore()) {
                if (!report.isAnalyzedByAI()) {
                    intelligenceService.analyzeReport(report, analyzedReport -> {
                        runOnUiThread(() -> {
                            // After AI analysis, upload to Firestore
                            uploadReportToFirestore(analyzedReport);
                            Log.d("OfflineSync", "Successfully re-analyzed and uploading report: " + analyzedReport.getId());
                        });
                    });
                } else {
                    // Already analyzed but not synced
                    uploadReportToFirestore(report);
                }
            }
        }
    }

    private void uploadReportToFirestore(ReportModel report) {
        reportsRef.document(report.getId()).set(report)
                .addOnSuccessListener(aVoid -> {
                    Log.d("Firestore", "Report uploaded: " + report.getId());
                    report.setSyncedWithFirestore(true);
                    saveReports(); // Save the synced state locally
                })
                .addOnFailureListener(e -> Log.e("Firestore", "Error uploading report", e));
    }

    private void syncGdacsFeed() {
        Toast.makeText(this, "Syncing GDACS feed with Cloud...", Toast.LENGTH_SHORT).show();
        gdacsService.getTopDisasters().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String rawJson = response.body().string();

                        // Handle potential double-encoding or leading text
                        if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                            rawJson = rawJson.substring(1, rawJson.length() - 1)
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\");
                        }

                        int jsonStart = rawJson.indexOf("{");
                        if (jsonStart != -1) {
                            rawJson = rawJson.substring(jsonStart);
                        }

                        GdacsService.GdacsResponse gdacsData = new Gson().fromJson(rawJson, GdacsService.GdacsResponse.class);

                        if (gdacsData != null && gdacsData.features != null) {
                            List<GdacsService.Feature> features = gdacsData.features;
                            int count = 0;
                            for (GdacsService.Feature feature : features) {
                                if (count >= 3) break;

                                double lat = 0, lng = 0;
                                if (feature.geometry != null && feature.geometry.coordinates != null) {
                                    Object coordsObj = feature.geometry.coordinates;
                                    if (coordsObj instanceof List) {
                                        List<?> coordsList = (List<?>) coordsObj;
                                        if (!coordsList.isEmpty() && coordsList.get(0) instanceof Double) {
                                            lng = (Double) coordsList.get(0);
                                            lat = (Double) coordsList.get(1);
                                        } else if (!coordsList.isEmpty() && coordsList.get(0) instanceof List) {
                                            List<?> firstPoint = (List<?>) coordsList.get(0);
                                            if (firstPoint.size() >= 2 && firstPoint.get(0) instanceof Double) {
                                                lng = (Double) firstPoint.get(0);
                                                lat = (Double) firstPoint.get(1);
                                            }
                                        }
                                    }
                                }

                                if (lat == 0 && lng == 0) continue;

                                String summary = feature.properties.name != null ? feature.properties.name : "Unknown Disaster";
                                String description = feature.properties.description != null ? feature.properties.description : summary;

                                ReportModel gdacsReport = new ReportModel(description, lat, lng);

                                // Deterministic ID to avoid duplicates in Firestore
                                String gdacsId = "gdacs_" + Math.abs((summary + lat + lng).hashCode());
                                gdacsReport.setId(gdacsId);

                                gdacsReport.setCategory(feature.properties.eventtype != null ? feature.properties.eventtype : "Disaster");
                                gdacsReport.setSummary(summary);
                                gdacsReport.setSource("GDACS");
                                gdacsReport.setAnalyzedByAI(true);

                                int sev = 5;
                                if (feature.properties.severity != null) {
                                    String sevStr = feature.properties.severity.toString();
                                    if (sevStr.equalsIgnoreCase("red")) sev = 9;
                                    else if (sevStr.equalsIgnoreCase("orange")) sev = 6;
                                    else if (sevStr.equalsIgnoreCase("green")) sev = 3;
                                    else {
                                        try {
                                            sev = (int) Double.parseDouble(sevStr);
                                        } catch (Exception e) { sev = 5; }
                                    }
                                }
                                gdacsReport.setSeverityScore(sev);
                                gdacsReport.setRelevantAuthority("International Relief (GDACS)");

                                // Save GDACS data to Firestore (satisfies user request)
                                uploadReportToFirestore(gdacsReport);
                                count++;
                            }

                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Synced top disasters to Firestore database", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e("GDACS", "Final parsing error: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to parse feed", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("GDACS", "Sync failed: " + t.getMessage());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReportsAsync(() -> {
            runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                refreshMap();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reportsListener != null) {
            reportsListener.remove();
        }
    }

    private void showSosConfirmation() {
        final String[] sosOptions = {"Police", "Fire Brigade", "Ambulance"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Emergency Service")
                .setItems(sosOptions, (dialog, which) -> {
                    String selectedAuthority = sosOptions[which];
                    String category = "SOS"; // Default category
                    if (selectedAuthority.equals("Fire Brigade")) {
                        category = "Fire";
                    } else if (selectedAuthority.equals("Ambulance")) {
                        category = "Medical";
                    }
                    triggerSosWithAuthority(selectedAuthority, category);
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_menu_call)
                .show();
    }

    private void triggerSosWithAuthority(String relevantAuthority, String category) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            lastKnownLocation = location;
            double lat = location != null ? location.getLatitude() : 23.3441;
            double lng = location != null ? location.getLongitude() : 85.3096;

            ReportModel sosReport = new ReportModel("IMMEDIATE SOS: User needs urgent rescue!", lat, lng);
            sosReport.setCategory(category); // Set category based on selection
            sosReport.setSeverityScore(10);
            sosReport.setSummary("Critical SOS request triggered by user.");
            sosReport.setRelevantAuthority(relevantAuthority); // Set authority based on selection
            sosReport.setSource("RescueNet");
            sosReport.setAnalyzedByAI(true); // SOS is pre-defined
            if (currentAnonymousUserId != null) {
                sosReport.setAnonymousId(currentAnonymousUserId);
            }

            processNewReportDirectly(sosReport);

            new MaterialAlertDialogBuilder(this)
                    .setTitle("SOS DISPATCHED")
                    .setMessage("Your SOS request has been received and emergency units are being dispatched via " + relevantAuthority)
                    .setPositiveButton("UNDERSTOOD", null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        });
    }

    private void processNewReportDirectly(ReportModel report) {
        // For RescueNet reports, upload to Firestore
        uploadReportToFirestore(report);

        // UI will update via Firestore listener, but for immediate feedback:
        if (!activeReports.contains(report)) {
            activeReports.add(0, report);
            addMarkerToMap(report);
            adapter.notifyItemInserted(0);
            recyclerView.scrollToPosition(0);
            saveReports();
            refreshMap();
        }
    }

    @Override
    public void onReportClick(ReportModel report) {
        Intent intent = new Intent(this, ReportDetailActivity.class);
        String reportJson = new Gson().toJson(report);
        intent.putExtra("report_data", reportJson);
        startActivity(intent);
    }

    private void showReportDialog() {
        BottomSheetDialog reportDialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report, null);
        reportDialog.setContentView(dialogView);

        TextInputEditText editDesc = dialogView.findViewById(R.id.edit_report_desc);
        View btnSubmit = dialogView.findViewById(R.id.btn_submit_report);

        btnSubmit.setOnClickListener(v -> {
            String description = editDesc.getText().toString().trim();
            if (description.isEmpty()) {
                editDesc.setError("Please describe the incident");
                return;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Confirm Submission")
                    .setMessage("Are you sure you want to submit this report? Your location will be shared with authorities for analysis.")
                    .setPositiveButton("Submit", (dialog, which) -> {
                        reportDialog.dismiss();
                        submitUserReport(description);
                    })
                    .setNegativeButton("Edit", null)
                    .show();
        });

        reportDialog.show();
    }

    private void submitUserReport(String description) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            lastKnownLocation = location;
            double lat = 23.3441;
            double lng = 85.3096;
            if (location != null) {
                lat = location.getLatitude();
                lng = location.getLongitude();
            }

            ReportModel userReport = new ReportModel(description, lat, lng);
            userReport.setSource("RescueNet");
            if (currentAnonymousUserId != null) {
                userReport.setAnonymousId(currentAnonymousUserId);
            }
            processNewReport(userReport, true);
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        isMapReady = true;

        LatLng startLoc = new LatLng(23.3441, 85.3096);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLoc, 14));

        enableMyLocation();

        // Conditionally refresh map and adapter if reports are already loaded
        if (areReportsLoaded) {
            refreshMap();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);

                fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                    if (location != null) {
                        lastKnownLocation = location;
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                        sortReportsByProximity();
                    }
                });
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void processNewReport(ReportModel rawReport, boolean showToast) {
        for (int i = 0; i < activeReports.size(); i++) {
            ReportModel existing = activeReports.get(i);
            if (existing != null && calculateDistance(rawReport, existing) < 100) {
                existing.incrementCount();
                if (showToast) {
                    Toast.makeText(this, "Report merged with existing incident.", Toast.LENGTH_SHORT).show();
                }

                if ("RescueNet".equals(existing.getSource())) {
                    uploadReportToFirestore(existing);
                }

                adapter.notifyItemChanged(i);
                saveReports();
                refreshMap();
                return;
            }
        }

        intelligenceService.analyzeReport(rawReport, analyzedReport -> {
            runOnUiThread(() -> {
                // Upload to Firestore
                uploadReportToFirestore(analyzedReport);

                if (showToast) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(analyzedReport.isAnalyzedByAI() ? "Report Forwarded" : "Report Saved (Offline)")
                            .setMessage(analyzedReport.isAnalyzedByAI() ?
                                    "Your incident report has been analyzed and successfully forwarded to: " + analyzedReport.getRelevantAuthority() :
                                    "You are offline. Your report has been saved locally and will be analyzed/forwarded automatically once you are back online.")
                            .setPositiveButton("OK", null)
                            .setIcon(analyzedReport.isAnalyzedByAI() ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_dialog_alert)
                            .show();
                }
            });
        });
    }

    private void sortReportsByProximity() {
        if (lastKnownLocation == null || activeReports.isEmpty()) return;

        Collections.sort(activeReports, (r1, r2) -> {
            double d1 = calculateDistanceToLocation(lastKnownLocation, r1);
            double d2 = calculateDistanceToLocation(lastKnownLocation, r2);
            return Double.compare(d1, d2);
        });
        adapter.notifyDataSetChanged();
    }

    private double calculateDistanceToLocation(Location loc, ReportModel report) {
        float[] results = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                report.getPosition().latitude, report.getPosition().longitude, results);
        return results[0];
    }

    private void addMarkerToMap(ReportModel report) {
        if (mMap == null || report == null) return;

        int color;
        int iconResId;
        String category = report.getCategory() != null ? report.getCategory().toUpperCase() : "OTHER";

        if (category.contains("FIRE")) {
            color = Color.RED;
            iconResId = R.drawable.ic_fire;
        } else if (category.contains("MEDICAL") || category.contains("AMBULANCE")) {
            color = Color.rgb(233, 30, 99); // Pink
            iconResId = R.drawable.ic_medical;
        } else if (category.contains("SOS") || category.contains("EMERGENCY")) {
            color = Color.RED;
            iconResId = R.drawable.ic_sos;
        } else if (category.contains("FLOOD") || category.contains("WATER")) {
            color = Color.BLUE;
            iconResId = R.drawable.ic_flood;
        } else if (category.contains("ACCIDENT") || category.contains("CRASH")) {
            color = Color.rgb(255, 152, 0); // Orange
            iconResId = R.drawable.ic_accident;
        } else {
            color = Color.GRAY;
            iconResId = android.R.drawable.ic_dialog_info;
        }

        mMap.addCircle(new CircleOptions()
                .center(report.getPosition())
                .radius(150)
                .fillColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)))
                .strokeColor(color)
                .strokeWidth(3f));

        mMap.addMarker(new MarkerOptions()
                .position(report.getPosition())
                .title(report.getCategory() + " (Sev: " + report.getSeverityScore() + ")")
                .snippet(report.getSummary() + " [" + report.getReportCount() + " reports]")
                .icon(getBitmapDescriptor(iconResId)));
    }

    private BitmapDescriptor getBitmapDescriptor(int resId) {
        Drawable drawable = ContextCompat.getDrawable(this, resId);
        if (drawable == null) return BitmapDescriptorFactory.defaultMarker();
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void refreshMap() {
        if (mMap == null) return;
        mMap.clear();
        for (ReportModel report : activeReports) {
            addMarkerToMap(report);
        }
    }

    private double calculateDistance(ReportModel r1, ReportModel r2) {
        double earthRadius = 6371000;
        double dLat = Math.toRadians(r2.getLatitude() - r1.getLatitude());
        double dLng = Math.toRadians(r2.getLongitude() - r1.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(r1.getLatitude())) * Math.cos(Math.toRadians(r2.getLatitude())) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private void loadTierA_MockData() {
        ReportModel r1 = new ReportModel("Huge fire in the main market", 23.3450, 85.3100);
        r1.setSource("RescueNet");
        r1.setAnalyzedByAI(true);
        processNewReport(r1, false);

        ReportModel r2 = new ReportModel("Flooding blocking the station road", 23.3420, 85.3050);
        r2.setSource("RescueNet");
        r2.setAnalyzedByAI(true);
        processNewReport(r2, false);

        ReportModel r3 = new ReportModel("Need food packets for 50 people", 23.3480, 85.3120);
        r3.setSource("RescueNet");
        r3.setAnalyzedByAI(true);
        processNewReport(r3, false);

        ReportModel r4 = new ReportModel("Major car accident on the highway", 23.3500, 85.3150);
        r4.setSource("RescueNet");
        r4.setAnalyzedByAI(true);
        processNewReport(r4, false);
    }

    private void saveReports() {
        backgroundExecutor.execute(() -> {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            Gson gson = new Gson();
            String json = gson.toJson(activeReports);
            editor.putString(KEY_REPORTS, json);
            editor.apply();
        });
    }

    private void loadReportsAsync(Runnable onLoadedCallback) {
        backgroundExecutor.execute(() -> {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Gson gson = new Gson();
            String json = sharedPreferences.getString(KEY_REPORTS, null);
            Type type = new TypeToken<ArrayList<ReportModel>>() {}.getType();
            List<ReportModel> loadedReports = gson.fromJson(json, type);

            activeReports.clear();
            if (loadedReports != null) {
                activeReports.addAll(loadedReports);
            }

            if (onLoadedCallback != null) {
                onLoadedCallback.run();
            }
        });
    }
}