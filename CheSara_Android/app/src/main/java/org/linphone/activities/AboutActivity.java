package org.linphone.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

public class AboutActivity extends MainActivity {
    private CoreListenerStub mListener;
    private ProgressDialog mProgress;
    private boolean mUploadInProgress;
    String content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOnBackPressGoHome = false;
        mAlwaysHideTabBar = true;

        // Uses the fragment container layout to inflate the about view instead of using a fragment
        View aboutView = LayoutInflater.from(this).inflate(R.layout.about, null, false);
        LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentContainer.addView(aboutView, params);

        if (isTablet()) {
            findViewById(R.id.fragmentContainer2).setVisibility(View.GONE);
        }

        TextView aboutVersion = findViewById(R.id.about_android_version);

        Intent intent = getIntent(); /*데이터 수신*/
        content = intent.getExtras().getString("content");

        TextView privacyPolicy = findViewById(R.id.privacy_policy_link);
        privacyPolicy.setText(content);

        Button btnRegist = aboutView.findViewById(R.id.btnRegist);

        btnRegist.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Calendar cal = Calendar.getInstance();

                        String cplace = content.split("장소 : ")[1].split("에서")[0];
                        String chour = content.split("시간 : ")[1].split("시")[0];
                        String cday = content.split("날짜 : ")[1].split("일")[0];

                        Date currentTime = Calendar.getInstance().getTime();
                        SimpleDateFormat weekdayFormat =
                                new SimpleDateFormat("EE", Locale.getDefault());
                        SimpleDateFormat dayFormat =
                                new SimpleDateFormat("dd", Locale.getDefault());
                        SimpleDateFormat monthFormat =
                                new SimpleDateFormat("MM", Locale.getDefault());
                        SimpleDateFormat yearFormat =
                                new SimpleDateFormat("yyyy", Locale.getDefault());

                        String year = yearFormat.format(currentTime);
                        String month = monthFormat.format(currentTime);
                        String day = dayFormat.format(currentTime);
                        Log.d("year", year);
                        Log.d("month", month);

                        int cmonth;
                        if (Integer.parseInt(day) > Integer.parseInt(cday)) {
                            cmonth = Integer.parseInt(month);
                        } else {
                            cmonth = Integer.parseInt(month) - 1;
                        }
                        int cchour = Integer.parseInt(chour) + 12;

                        cal.set(
                                Integer.parseInt(year),
                                cmonth,
                                Integer.parseInt(cday),
                                cchour,
                                0,
                                0);
                        Intent intent2 = new Intent(Intent.ACTION_EDIT);
                        intent2.setType("vnd.android.cursor.item/event");
                        intent2.putExtra("beginTime", cal.getTimeInMillis());
                        intent2.putExtra("allDay", false);

                        cal.set(
                                Integer.parseInt(year),
                                cmonth,
                                Integer.parseInt(cday),
                                cchour + 1,
                                0,
                                0);

                        intent2.putExtra("endTime", cal.getTimeInMillis());
                        intent2.putExtra("title", cplace + "에서 약속");
                        startActivity(intent2);
                    }
                });

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onLogCollectionUploadProgressIndication(
                            Core core, int offset, int total) {}

                    @Override
                    public void onLogCollectionUploadStateChanged(
                            Core core, Core.LogCollectionUploadState state, String info) {
                        if (state == Core.LogCollectionUploadState.InProgress) {
                            displayUploadLogsInProgress();
                        } else if (state == Core.LogCollectionUploadState.Delivered
                                || state == Core.LogCollectionUploadState.NotDelivered) {
                            mUploadInProgress = false;
                            if (mProgress != null) mProgress.dismiss();
                        }
                    }
                };
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent(); /*데이터 수신*/
        String fileName = intent.getExtras().getString("filename");

        showTopBarWithTitle(fileName);
        if (getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views)) {
            hideTabBar();
        }

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }
    }

    @Override
    public void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mListener = null;
        mProgress = null;

        super.onDestroy();
    }

    private void displayUploadLogsInProgress() {
        if (mUploadInProgress) {
            return;
        }
        mUploadInProgress = true;

        mProgress = ProgressDialog.show(this, null, null);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.light_grey_color));
        d.setAlpha(200);
        mProgress
                .getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        mProgress.getWindow().setBackgroundDrawable(d);
        mProgress.setContentView(R.layout.wait_layout);
        mProgress.show();
    }
}
