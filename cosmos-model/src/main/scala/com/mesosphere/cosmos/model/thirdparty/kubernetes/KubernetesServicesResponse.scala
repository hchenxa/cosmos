package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesServicesResponse (
  apiVersion: String,
  kind: String,
  metadata: Map[String, String],
  items: List[KubernetesService]
)