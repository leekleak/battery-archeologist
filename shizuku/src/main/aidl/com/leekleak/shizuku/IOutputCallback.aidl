package com.leekleak.shizuku;

oneway interface IOutputCallback {
    void onOutput(String chunk);
    void onError(String chunk);
    void onComplete(int exitCode);
}
