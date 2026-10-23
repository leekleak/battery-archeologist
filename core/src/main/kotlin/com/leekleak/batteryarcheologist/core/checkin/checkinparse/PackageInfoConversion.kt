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

// Conversion from the app's lightweight PackageInfoModel to the generated
// usagestats PackageInfo proto. The Go parser receives these protos directly; keeping
// the conversion here lets the checkin parser merge checkin `uid` rows with package
// metadata from a bug report in exactly the same shape.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import com.leekleak.batteryarcheologist.proto.usagestats.AndroidPackageInfo

/** Converts an app package model into the generated usagestats PackageInfo proto. */
internal fun PackageInfoModel.toProtoPackageInfo(): AndroidPackageInfo.PackageInfo =
    AndroidPackageInfo.PackageInfo.newBuilder()
        .setPkgName(name)
        .setUid(uid)
        .setVersionCode(versionCode.toInt())
        .setVersionName(versionName)
        .setFirstInstallTime(firstInstallTime)
        .setLastUpdateTime(lastUpdateTime)
        .setSharedUserId(sharedUserId)
        .build()
