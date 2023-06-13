package com.example.camera2demo;

public class HDRP {

    static {
        System.loadLibrary("camera2demo");
    }

    public native static int ImageHDRProcess();
}

