package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesPod(
  apiVersion: String,
  kind: String,
  metadata: Option[KubernetesPodMetadata],
  spec: Option[KubernetesPodSpec]
)

