package com.upwork.jaycee.echolocate;

import android.app.Application;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
        mailTo = "jaycee.lock@gmail.com", // my email here
        customReportContent = { ReportField.APP_VERSION_CODE, ReportField.APP_VERSION_NAME, ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL, ReportField.CUSTOM_DATA, ReportField.STACK_TRACE, ReportField.LOGCAT },
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash_toast_text)
public class ApplicationBase extends Application
{
    @Override
    public void onCreate()
    {
        /* The following line triggers the initialization of ACRA */
        super.onCreate();
        ACRA.init(this);
    }
}
