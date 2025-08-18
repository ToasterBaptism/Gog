package com.facebook.react;

import android.app.Application;
import com.facebook.react.ReactPackage;
import java.util.Arrays;
import java.util.List;

/**
 * Manual PackageList to prevent autolinking conflicts
 * Autolinking is disabled - packages are manually registered in MainApplication.kt
 */
public class PackageList {
    private Application mApplication;

    public PackageList(Application application) {
        mApplication = application;
    }

    public List<ReactPackage> getPackages() {
        // Return empty list - packages are manually registered
        return Arrays.<ReactPackage>asList();
    }
}