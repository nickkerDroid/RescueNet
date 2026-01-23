package info.fortheease.rescuenet;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    private List<ReportModel> reportList;
    private Context context;
    private OnReportClickListener clickListener;

    public interface OnReportClickListener {
        void onReportClick(ReportModel report);
    }

    public ReportAdapter(Context context, List<ReportModel> reportList, OnReportClickListener clickListener) {
        this.context = context;
        this.reportList = reportList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReportModel report = reportList.get(position);

        holder.txtCategory.setText(report.getCategory());
        holder.txtSummary.setText(report.getSummary());
        holder.txtSeverity.setText("Severity: " + report.getSeverityScore());
        holder.txtSource.setText("Source: " + report.getSource());
        
        int count = report.getReportCount();
        String countText = count == 1 ? "Reported by 1 person" : "Reported by " + count + " people";
        holder.txtReportCount.setText(countText);

        if (report.getSeverityScore() >= 8) {
            holder.statusStrip.setBackgroundColor(Color.parseColor("#D32F2F"));
            holder.txtSeverity.setTextColor(Color.parseColor("#D32F2F"));
        } else if (report.getSeverityScore() >= 4) {
            holder.statusStrip.setBackgroundColor(Color.parseColor("#F57C00"));
            holder.txtSeverity.setTextColor(Color.parseColor("#F57C00"));
        } else {
            holder.statusStrip.setBackgroundColor(Color.parseColor("#FBC02D"));
            holder.txtSeverity.setTextColor(Color.parseColor("#FBC02D"));
        }

        holder.itemView.setOnClickListener(v -> clickListener.onReportClick(report));
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtCategory, txtSummary, txtSeverity, txtReportCount, txtSource;
        View statusStrip;
        MaterialCardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCategory = itemView.findViewById(R.id.txt_category);
            txtSummary = itemView.findViewById(R.id.txt_summary);
            txtSeverity = itemView.findViewById(R.id.txt_severity);
            txtReportCount = itemView.findViewById(R.id.txt_report_count);
            txtSource = itemView.findViewById(R.id.txt_source);
            statusStrip = itemView.findViewById(R.id.status_strip);
            cardView = itemView.findViewById(R.id.card_view);
        }
    }
}
