// Copyright 2016 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Kotlin port of the shared-UID and constant-UID tables in
// third_party/battery-historian/checkinparse/checkin_parse.go.
//
// Keeping these tables identical to upstream matters for presentation: the app name
// shown for a UID is chosen from these labels, so changing a label changes the parsed
// report's app names.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

/** Maps a sharedUserId label to a predefined, human-readable group name. */
internal val sharedUidLabelMap: Map<String, String> = mapOf(
    "android.media" to "MEDIA",
    "android.uid.bluetooth" to "BLUETOOTH",
    "android.uid.nfc" to "NFC",
    "android.uid.phone" to "RADIO",
    "android.uid.shared" to "CONTACTS_PROVIDER",
    "android.uid.shell" to "SHELL",
    "android.uid.system" to "ANDROID_SYSTEM",
    "android.uid.systemui" to "SYSTEM_UI",
    "com.google.android.calendar.uid.shared" to "GOOGLE_CALENDAR",
    "com.google.uid.shared" to "GOOGLE_SERVICES",
)

/** Maps well-known package names to the predefined group name of their shared UID. */
internal val packageNameToSharedUidMap: Map<String, String> = mapOf(
    // com.google.uid.shared
    "com.google.android.apps.gtalkservice" to "GOOGLE_SERVICES",
    "com.google.android.backuptransport" to "GOOGLE_SERVICES",
    "com.google.android.gms" to "GOOGLE_SERVICES",
    "com.google.android.gms.car.userfeedback" to "GOOGLE_SERVICES",
    "com.google.android.googleapps" to "GOOGLE_SERVICES",
    "com.google.android.gsf" to "GOOGLE_SERVICES",
    "com.google.android.gsf.login" to "GOOGLE_SERVICES",
    "com.google.android.gsf.notouch" to "GOOGLE_SERVICES",
    "com.google.android.providers.gmail" to "GOOGLE_SERVICES",
    "com.google.android.sss.authbridge" to "GOOGLE_SERVICES",
    "com.google.gch.gateway" to "GOOGLE_SERVICES",

    // com.google.android.calendar.uid.shared
    "com.android.calendar" to "GOOGLE_CALENDAR",
    "com.google.android.calendar" to "GOOGLE_CALENDAR",
    "com.google.android.syncadapters.calendar" to "GOOGLE_CALENDAR",

    // android.uid.system
    "android" to "ANDROID_SYSTEM",
    "com.android.changesettings" to "ANDROID_SYSTEM",
    "com.android.inputdevices" to "ANDROID_SYSTEM",
    "com.android.keychain" to "ANDROID_SYSTEM",
    "com.android.location.fused" to "ANDROID_SYSTEM",
    "com.android.providers.settings" to "ANDROID_SYSTEM",
    "com.android.settings" to "ANDROID_SYSTEM",
    "com.google.android.canvas.settings" to "ANDROID_SYSTEM",
    "com.lge.SprintHiddenMenu" to "ANDROID_SYSTEM",
    "com.nvidia.tegraprofiler.security" to "ANDROID_SYSTEM",
    "com.qualcomm.atfwd" to "ANDROID_SYSTEM",
    "com.qualcomm.display" to "ANDROID_SYSTEM",

    // android.uid.phone, associated with UID 1001
    "com.android.phone" to "RADIO",
    "com.android.providers.telephony" to "RADIO",
    "com.android.sdm.plugins.connmo" to "RADIO",
    "com.android.sdm.plugins.dcmo" to "RADIO",
    "com.android.sdm.plugins.sprintdm" to "RADIO",
    "com.htc.android.qxdm2sd" to "RADIO",
    "com.android.mms.service" to "RADIO",
    "com.android.server.telecom" to "RADIO",
    "com.android.sprint.lifetimedata" to "RADIO",
    "com.android.stk" to "RADIO",
    "com.motorola.service.ims" to "RADIO",
    "com.qualcomm.qti.imstestrunner" to "RADIO",
    "com.qualcomm.qti.rcsbootstraputil" to "RADIO",
    "com.qualcomm.qti.rcsimsbootstraputil" to "RADIO",
    "com.qualcomm.qcrilmsgtunnel" to "RADIO",
    "com.qualcomm.shutdownlistner" to "RADIO",
    "org.codeaurora.ims" to "RADIO",
    "com.asus.atcmd" to "RADIO",
    "com.mediatek.imeiwriter" to "RADIO",

    // android.uid.nfc
    "com.android.nfc" to "NFC",
    "com.android.nfc3" to "NFC",
    "com.google.android.uiccwatchdog" to "NFC",

    // android.uid.shared
    "com.android.contacts" to "CONTACTS_PROVIDER",
    "com.android.providers.applications" to "CONTACTS_PROVIDER",
    "com.android.providers.contacts" to "CONTACTS_PROVIDER",
    "com.android.providers.userdictionary" to "CONTACTS_PROVIDER",

    // android.media
    "com.android.gallery" to "MEDIA",
    "com.android.providers.downloads" to "MEDIA",
    "com.android.providers.downloads.ui" to "MEDIA",
    "com.android.providers.drm" to "MEDIA",
    "com.android.providers.media" to "MEDIA",

    // android.uid.systemui
    "com.android.keyguard" to "SYSTEM_UI",
    "com.android.systemui" to "SYSTEM_UI",

    // android.uid.bluetooth
    "com.android.bluetooth" to "BLUETOOTH",

    // android.uid.shell
    "com.android.shell" to "SHELL",
)

/**
 * Constant UIDs defined in `system/core/include/private/android_filesystem_config.h`.
 *
 * GIDs are excluded. These should never be renumbered.
 */
internal val knownUids: Map<Int, String> = mapOf(
    0 to "ROOT", // traditional unix root user
    1000 to "ANDROID_SYSTEM", // system server
    1001 to "RADIO", // telephony subsystem, RIL
    1002 to "BLUETOOTH", // bluetooth subsystem
    1003 to "GRAPHICS", // graphics devices
    1004 to "INPUT", // input devices
    1005 to "AUDIO", // audio devices
    1006 to "CAMERA", // camera devices
    1007 to "LOG", // log devices
    1008 to "COMPASS", // compass device
    1009 to "MOUNT", // mountd socket
    1010 to "WIFI", // wifi subsystem
    1011 to "ADB", // android debug bridge (adbd)
    1012 to "INSTALL", // group for installing packages
    1013 to "MEDIA", // mediaserver process
    1014 to "DHCP", // dhcp client
    1015 to "SDCARD_RW", // external storage write access
    1016 to "VPN", // vpn system
    1017 to "KEYSTORE", // keystore subsystem
    1018 to "USB", // USB devices
    1019 to "DRM", // DRM server
    1020 to "MDNSR", // MulticastDNSResponder (service discovery)
    1021 to "GPS", // GPS daemon
    1023 to "MEDIA_RW", // internal media storage write access
    1024 to "MTP", // MTP USB driver access
    1026 to "DRMRPC", // group for drm rpc
    1027 to "NFC", // nfc subsystem
    1028 to "SDCARD_R", // external storage read access
    1029 to "CLAT", // clat part of nat464
    1030 to "LOOP_RADIO", // loop radio devices
    1031 to "MEDIA_DRM", // MediaDrm plugins
    1032 to "PACKAGE_INFO", // access to installed package details
    1033 to "SDCARD_PICS", // external storage photos access
    1034 to "SDCARD_AV", // external storage audio/video access
    1035 to "SDCARD_ALL", // access all users external storage
    1036 to "LOGD", // log daemon
    1037 to "SHARED_RELRO", // creator of shared GNU RELRO files
    1038 to "DBUS", // dbus-daemon IPC broker process
    1039 to "TLSDATE", // tlsdate unprivileged user
    1040 to "MEDIA_EX", // mediaextractor process
    1041 to "AUDIOSERVER", // audioserver process
    1042 to "METRICS_COLL", // metrics_collector process
    1043 to "METRICSD", // metricsd process
    1044 to "WEBSERV", // webservd process
    1045 to "DEBUGGERD", // debuggerd unprivileged user
    1046 to "MEDIA_CODEC", // mediacodec process
    1047 to "CAMERASERVER", // cameraserver process
    1048 to "FIREWALL", // firewalld process
    1049 to "TRUNKS", // trunksd process (TPM daemon)
    1050 to "NVRAM", // Access-controlled NVRAM
    1051 to "DNS", // DNS resolution daemon (system: netd)
    1052 to "DNS_TETHER", // DNS resolution daemon (tether: dnsmasq)
    1053 to "WEBVIEW_ZYGOTE", // WebView zygote process
    1054 to "VEHICLE_NETWORK", // Vehicle network service
    1055 to "MEDIA_AUDIO", // GID for audio files on internal media storage
    1056 to "MEDIA_VIDEO", // GID for video files on internal media storage
    1057 to "MEDIA_IMAGE", // GID for image files on internal media storage
    1058 to "TOMBSTONED", // tombstoned user
    1059 to "MEDIA_OBB", // GID for OBB files on internal media storage
    1060 to "ESE", // embedded secure element (eSE) subsystem
    1061 to "OTA_UPDATE", // resource tracking UID for OTA updates
    2000 to "SHELL", // adb and debug shell user
    2001 to "CACHE", // cache access
    2002 to "DIAG", // access to diagnostic resources
    3001 to "NET_BT_ADMIN", // bluetooth: create any socket
    3002 to "NET_BT", // bluetooth: create sco, rfcomm or l2cap sockets
    3003 to "INET", // can create AF_INET and AF_INET6 sockets
    3004 to "NET_RAW", // can create raw INET sockets
    3005 to "NET_ADMIN", // can configure interfaces and routing tables
    3006 to "NET_BW_STATS", // read bandwidth statistics
    3007 to "NET_BW_ACCT", // change bandwidth statistics accounting
    3008 to "NET_BT_STACK", // bluetooth: access config files
    3009 to "READPROC", // allow /proc read access
    3010 to "WAKELOCK", // allow system wakelock read/write access
    9997 to "EVERYBODY", // shared between all apps in the same profile
    9998 to "MISC", // access to misc storage
    9999 to "NOBODY",
)

/** Returns the predefined group name for the given package name, or an empty string. */
fun packageUidGroupName(pkg: String): String = packageNameToSharedUidMap[pkg].orEmpty()

/** Returns a predefined UID group name for the given sharedUserId label. */
fun groupName(sharedUidLabel: String): String = sharedUidLabelMap[sharedUidLabel].orEmpty()
