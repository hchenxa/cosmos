package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion

case class InstallMarathonResponse(
  packageName: String,
  packageVersion: PackageDetailsVersion,
  appId: AppId
) extends InstallResponse(packageName, packageVersion)
