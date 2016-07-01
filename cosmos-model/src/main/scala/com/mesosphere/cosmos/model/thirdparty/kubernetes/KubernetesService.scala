package com.mesosphere.cosmos.model.thirdparty.kubernetes

import com.mesosphere.universe.{ReleaseVersion, PackageDetailsVersion}

case class KubernetesService(
  apiVersion: String,
  kind: String,
  metadata: KubernetesServiceMetadata,
  spec: KubernetesServiceSpec
) extends KubernetesObject (apiVersion, kind) {
  def packageName: Option[String] = metadata.labels.get(KubernetesService.nameLabel)

  def packageReleaseVersion: Option[ReleaseVersion] = metadata.labels.get(KubernetesService.releaseLabel).map(ReleaseVersion)

  def packageVersion: Option[PackageDetailsVersion] = metadata.labels.get(KubernetesService.versionLabel).map(PackageDetailsVersion)

  def packageRepository: Option[String] = metadata.labels.get(KubernetesService.repositoryLabel)
}

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

object KubernetesService {
  val frameworkNameLabel = "DCOS_PACKAGE_FRAMEWORK_NAME"
  val isFrameworkLabel = "DCOS_PACKAGE_IS_FRAMEWORK"
  val metadataLabel = "DCOS_PACKAGE_METADATA"
  val nameLabel = "DCOS_PACKAGE_NAME"
  val registryVersionLabel = "DCOS_PACKAGE_REGISTRY_VERSION"
  val releaseLabel = "DCOS_PACKAGE_RELEASE"
  val repositoryLabel = "DCOS_PACKAGE_SOURCE"
  val versionLabel = "DCOS_PACKAGE_VERSION"
  val commandLabel = "DCOS_PACKAGE_COMMAND"
}