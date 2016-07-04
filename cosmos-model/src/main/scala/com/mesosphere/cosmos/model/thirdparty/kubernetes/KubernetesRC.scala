package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesRC(
  kind: Option[String],
  apiVersion: Option[String],
  metadata: KubernetesRCMetadata,
  spec: KubernetesRCSpec
)

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
  selector: Map[String, String],
  template: KubernetesRCTemplate
)

case class KubernetesRCTemplate(
  metadata: KubernetesRCTemplateMetadata,
  spec: KubernetesRCTemplateSpec
)

case class KubernetesRCTemplateMetadata(
  name: Option[String],
  labels: Map[String, String]
)

case class KubernetesRCTemplateSpec(
  containers: List[KubernetesRCTemplateContainer],
  restartPolicy: String,
  terminationGracePeriodSeconds: Int,
  dnsPolicy: String,
  securityContext: Option[Map[String, String]]
)

case class KubernetesRCTemplateContainer(
  name: String,
  image: String,
//  command: Option[List[String]],
//  resources: Option[KubernetesContainerResource],
//  volumeMounts: ,
//  env: ,
  terminationMessagePath: String
//  imagePullPolicy: String
//  securityContext: Option[Map[String, String]]
)

case class KubernetesContainerResource(
  requests: Map[String, String]
)

case class KubernetesRCStatus(
  replicas: Int,
  fullyLabeledReplicas: Int,
  observedGeneration: Int
)