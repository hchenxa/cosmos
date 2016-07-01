package com.mesosphere.cosmos.model

case class KubernetesListResponse(
  packages: Seq[KubernetesInstallation]
)
