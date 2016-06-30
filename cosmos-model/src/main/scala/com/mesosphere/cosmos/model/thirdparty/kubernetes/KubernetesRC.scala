package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesRC(
  apiVersion: String,
  kind: String,
  metadata: KubernetesRCMetadata,
  spec: KubernetesRCSpec
) extends KubernetesObject (apiVersion, kind)

case class KubernetesRCMetadata(
  name: String,
  namespace: String,
  selfLink: String,
  uid: String,
  creationTimestamp: String,
  labels: Map[String, String]
)

case class KubernetesRCSpec(
  replicas: Int,
  selector: Map[String, String]
  
)

case class KubernetesRCTemplate(
  metadata: KubernetesRCTemplateMetadata,
  spec: KubernetesRCTemplateSpec
)

case class KubernetesRCTemplateMetadata(
  creationTimestamp: String,
  labels: Map[String, String]
)

case class KubernetesRCTemplateSpec(
  containers: List[KubernetesRCTemplateContainer],
  restartPolicy: String,
  terminationGracePeriodSeconds: Int,
  dnsPolicy: String,
  securityContext: Map[String, String]
)

case class KubernetesRCTemplateContainer(
  name: String,
  image: String,
  command: List[String],
  resources: Map[String, String],
  terminationMessagePath: String,
  imagePullPolicy: String
)

case class KubernetesRCStatus(
  replicas: Int,
  fullyLabeledReplicas: Int,
  observedGeneration: Int
)