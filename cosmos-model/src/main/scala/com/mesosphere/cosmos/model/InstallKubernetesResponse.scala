package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion

case class InstallKubernetesResponse(
  packageName: String,
  packageVersion: PackageDetailsVersion,
  kind: String,
  apiVersion: String
)
