package com.facebook.react;

import android.app.Application;
import com.facebook.react.ReactPackage;
import com.facebook.react.shell.MainReactPackage;
import java.util.Arrays;
import java.util.List;

/**
 * Manual PackageList to prevent autolinking conflicts
 * Core React Native packages included, third-party packages manually registered in MainApplication.kt
 */
public class PackageList {
    private Application mApplication;

    public PackageList(Application application) {
        mApplication = application;
    }

    public List<ReactPackage> getPackages() {
        // Include core React Native packages
        return Arrays.<ReactPackage>asList(
            new MainReactPackage()
        );
    }
}