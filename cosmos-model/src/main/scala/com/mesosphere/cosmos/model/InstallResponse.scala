package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion

abstract class InstallResponse(
  packageName: String,
  packageVersion: PackageDetailsVersion
)
