package info.fortheease.rescuenet;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.firestore.Exclude;

public class ReportModel {
    private String id;
    private String description;
    private double latitude;
    private double longitude;

    // AI Enriched Fields
    private int severityScore; // 1-10
    private String category; // Fire, Flood, Medical
    private String summary;
    private String relevantAuthority; // Police, Fire Dept, Ambulance, etc.
    private int reportCount = 1; // For deduplication
    private int resolvedCount = 0; // Number of people who marked it as resolved
    private String source = "RescueNet"; // Default source
    private boolean isAnalyzedByAI = false;
    private boolean isSyncedWithFirestore = false;

    // Required for Firestore
    public ReportModel() {
    }

    public ReportModel(String description, double lat, double lng) {
        this.description = description;
        this.latitude = lat;
        this.longitude = lng;
        this.id = String.valueOf(System.currentTimeMillis());
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Exclude
    public LatLng getPosition() { return new LatLng(latitude, longitude); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getSeverityScore() { return severityScore; }
    public void setSeverityScore(int severityScore) { this.severityScore = severityScore; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRelevantAuthority() { return relevantAuthority; }
    public void setRelevantAuthority(String relevantAuthority) { this.relevantAuthority = relevantAuthority; }

    public int getReportCount() { return reportCount; }
    public void setReportCount(int reportCount) { this.reportCount = reportCount; }

    public void incrementCount() { this.reportCount++; }

    public int getResolvedCount() { return resolvedCount; }
    public void setResolvedCount(int resolvedCount) { this.resolvedCount = resolvedCount; }

    public void incrementResolvedCount() { this.resolvedCount++; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public boolean isAnalyzedByAI() { return isAnalyzedByAI; }
    public void setAnalyzedByAI(boolean analyzedByAI) { isAnalyzedByAI = analyzedByAI; }

    public boolean isSyncedWithFirestore() { return isSyncedWithFirestore; }
    public void setSyncedWithFirestore(boolean syncedWithFirestore) { isSyncedWithFirestore = syncedWithFirestore; }
}
