package info.fortheease.rescuenet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ReportDetailActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "RescueNetPrefs";
    private static final String KEY_REPORTS = "activeReports";
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);

        db = FirebaseFirestore.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Get the ReportModel from Intent
        String reportJson = getIntent().getStringExtra("report_data");
        ReportModel report = new Gson().fromJson(reportJson, ReportModel.class);

        if (report != null) {
            populateDetails(report);
        }
    }

    private void populateDetails(ReportModel report) {
        TextView txtCategory = findViewById(R.id.detail_category);
        TextView txtSeverity = findViewById(R.id.detail_severity);
        TextView txtCount = findViewById(R.id.detail_count);
        TextView txtSummary = findViewById(R.id.detail_summary);
        TextView txtDescription = findViewById(R.id.detail_description);
        MaterialButton btnNavigate = findViewById(R.id.btn_navigate);
        MaterialButton btnMarkResolved = findViewById(R.id.btn_mark_resolved);

        txtCategory.setText(report.getCategory());
        txtSeverity.setText("Severity: " + report.getSeverityScore() + "/10");
        txtCount.setText(report.getReportCount() + (report.getReportCount() == 1 ? " Report" : " Reports"));
        txtSummary.setText(report.getSummary());
        txtDescription.setText(report.getDescription());

        btnNavigate.setOnClickListener(v -> {
            String uri = "google.navigation:q=" + report.getLatitude() + "," + report.getLongitude();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                String webUri = "https://www.google.com/maps/dir/?api=1&destination=" +
                        report.getLatitude() + "," + report.getLongitude();
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUri)));
            }
        });

        // Hide mark as resolved feature when source is GDACS
        if ("GDACS".equals(report.getSource())) {
            btnMarkResolved.setVisibility(View.GONE);
        } else {
            btnMarkResolved.setVisibility(View.VISIBLE);
            btnMarkResolved.setOnClickListener(v -> handleMarkAsResolved(report));
        }
    }

    private void handleMarkAsResolved(ReportModel report) {
        // 1. Update Firestore if it's a RescueNet report
        if ("RescueNet".equals(report.getSource())) {
            DocumentReference docRef = db.collection("reports").document(report.getId());
            
            report.incrementResolvedCount();
            
            if (report.getResolvedCount() > report.getReportCount()) {
                docRef.delete()
                        .addOnSuccessListener(aVoid -> {
                            Log.d("Firestore", "Incident resolved and deleted");
                            updateLocalCacheAndFinish(report, true);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("Firestore", "Error deleting resolved report", e);
                            updateLocalCacheAndFinish(report, false);
                        });
            } else {
                docRef.set(report)
                        .addOnSuccessListener(aVoid -> {
                            Log.d("Firestore", "Incident resolution count updated");
                            updateLocalCacheAndFinish(report, false);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("Firestore", "Error updating resolution count", e);
                            updateLocalCacheAndFinish(report, false);
                        });
            }
        } else {
            // For other sources, just handle locally (as they aren't in our Firestore)
            updateLocalCacheAndFinish(report, false);
        }
    }

    private void updateLocalCacheAndFinish(ReportModel report, boolean wasDeleted) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_REPORTS, null);
        Type type = new TypeToken<ArrayList<ReportModel>>() {}.getType();
        List<ReportModel> activeReports = gson.fromJson(json, type);

        if (activeReports == null) activeReports = new ArrayList<>();

        boolean removed = false;
        for (int i = 0; i < activeReports.size(); i++) {
            if (activeReports.get(i).getId().equals(report.getId())) {
                if (wasDeleted || report.getResolvedCount() > report.getReportCount()) {
                    activeReports.remove(i);
                    removed = true;
                } else {
                    activeReports.set(i, report);
                }
                break;
            }
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_REPORTS, gson.toJson(activeReports));
        editor.apply();

        if (removed) {
            Toast.makeText(this, "Incident resolved and removed.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Thank you for confirming resolution!", Toast.LENGTH_SHORT).show();
        }
        
        finish();
    }
}
