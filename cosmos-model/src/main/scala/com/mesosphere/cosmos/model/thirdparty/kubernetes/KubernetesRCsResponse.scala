package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesRCsResponse (
  apiVersion: String,
  kind: String,
  metadata: Map[String, String],
  items:  List[KubernetesRC]
)