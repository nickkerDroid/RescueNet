package info.fortheease.rescuenet;

import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class IntelligenceService {

    private static final String TAG = "GeminiAI";
    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public interface AnalysisCallback {
        void onAnalysisComplete(ReportModel report);
    }

    public IntelligenceService() {
        // Configure the model to return JSON
        GenerationConfig config = new GenerationConfig.Builder()
                .setResponseMimeType("application/json")
                .build();

        // Initialize with gemini-2.0-flash
        GenerativeModel aiModel = FirebaseAI.getInstance(GenerativeBackend.vertexAI())
                .generativeModel("gemini-2.0-flash", config);

        this.model = GenerativeModelFutures.from(aiModel);
    }

    public void analyzeReport(ReportModel report, AnalysisCallback callback) {
        String promptText = "Analyze this disaster report: \"" + report.getDescription() + "\". " +
                "Output strictly a JSON object with: " +
                "severity_score (1-10 integer), " +
                "category (String: strictly one of [Fire, Medical, SOS, Flood, Accident]), " +
                "summary (max 10 words), " +
                "and relevant_authority (String: e.g., Fire Department, Police, Municipal Corp, or Ambulance).";

        Content content = new Content.Builder()
                .addText(promptText)
                .build();

        ListenableFuture<GenerateContentResponse> responseFuture = model.generateContent(content);

        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String rawJson = result.getText();
                    Log.d(TAG, "AI Response: " + rawJson);

                    if (rawJson == null || rawJson.isEmpty()) {
                        applyOfflineFallback(report, callback);
                        return;
                    }

                    if (rawJson.contains("```")) {
                        rawJson = rawJson.replace("```json", "")
                                       .replace("```", "")
                                       .trim();
                    }

                    JsonObject analysis = JsonParser.parseString(rawJson).getAsJsonObject();

                    report.setSeverityScore(analysis.get("severity_score").getAsInt());
                    report.setCategory(analysis.get("category").getAsString());
                    report.setSummary(analysis.get("summary").getAsString());
                    report.setRelevantAuthority(analysis.get("relevant_authority").getAsString());
                    report.setAnalyzedByAI(true);

                    simulateForwarding(report);

                    callback.onAnalysisComplete(report);

                } catch (Exception e) {
                    Log.e(TAG, "Parsing error: " + e.getMessage());
                    applyOfflineFallback(report, callback);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "AI Failure: " + t.getMessage());
                applyOfflineFallback(report, callback);
            }
        }, executor);
    }

    private void simulateForwarding(ReportModel report) {
        Log.d(TAG, "SIMULATED: Forwarding report to " + report.getRelevantAuthority());
    }

    private void applyOfflineFallback(ReportModel report, AnalysisCallback callback) {
        String desc = report.getDescription().toLowerCase();
        if (desc.contains("trapped") || desc.contains("sos") || desc.contains("rescue")) {
            report.setSeverityScore(9);
            report.setCategory("SOS");
            report.setRelevantAuthority("Police & NDRF");
        } else if (desc.contains("fire") || desc.contains("burn")) {
            report.setSeverityScore(8);
            report.setCategory("Fire");
            report.setRelevantAuthority("Fire Department");
        } else if (desc.contains("medical") || desc.contains("hurt") || desc.contains("injured")) {
            report.setSeverityScore(7);
            report.setCategory("Medical");
            report.setRelevantAuthority("Ambulance");
        } else if (desc.contains("flood") || desc.contains("water") || desc.contains("drown")) {
            report.setSeverityScore(8);
            report.setCategory("Flood");
            report.setRelevantAuthority("Municipal Corp");
        } else if (desc.contains("accident") || desc.contains("crash")) {
            report.setSeverityScore(7);
            report.setCategory("Accident");
            report.setRelevantAuthority("Police");
        } else {
            report.setSeverityScore(3);
            report.setCategory("Other");
            report.setRelevantAuthority("General Service");
        }
        report.setSummary("(Offline) " + report.getDescription());
        report.setAnalyzedByAI(false); // Mark as not yet AI-analyzed
        callback.onAnalysisComplete(report);
    }
}
