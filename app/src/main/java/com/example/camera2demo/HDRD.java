package com.example.camera2demo;

public class HDRD {

    private final static String TAG = "Phildebug";

    static {
        System.loadLibrary("camera2demo");
    }

    public static native int ImageHDRDetection(int width, int height, byte[] y_data, byte[] uv_data);
}
