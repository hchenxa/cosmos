package com.mesosphere.cosmos.model

case class KubernetesListRequest(
  packageName: Option[String] = None,
  service: Option[String] = None,
  namespace: Option[String] = None
)
