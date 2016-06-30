package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesService(
  apiVersion: String,
  kind: String,
  metadata: KubernetesServiceMetadata,
  spec: KubernetesServiceSpec
) extends KubernetesObject (apiVersion, kind)

case class KubernetesServiceMetadata(
  name: String,
  namespace: String,
  selfLink: String,
  uid: String,
  creationTimestamp: String,
  labels: Map[String, String]
)

case class KubernetesServiceSpec(
  ports: List[KubernetesServicePorts],
  selector: Map[String, String],
  clusterIP: String,
  sessionAffinity: String
)

case class KubernetesServicePorts(
  protocol: String,
  port: Int,
  targetPort: Int
)