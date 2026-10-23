package com.leekleak.shizuku;

import com.leekleak.shizuku.IOutputCallback;

interface IBatteryArcheologistService {
    void destroy() = 16777114;
    void runShell(String command, IOutputCallback callback) = 1;
}
