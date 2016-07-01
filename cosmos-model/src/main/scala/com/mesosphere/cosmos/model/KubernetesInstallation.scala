package com.mesosphere.cosmos.model

case class KubernetesInstallation(
  service: String,
  namespace: String,
  packageInformation: InstalledPackageInformation
)
