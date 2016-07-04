package com.mesosphere.cosmos.model.thirdparty.kubernetes

import com.mesosphere.universe.{ReleaseVersion, PackageDetailsVersion}

case class KubernetesService(
  kind: Option[String],
  apiVersion: Option[String],
  metadata: KubernetesServiceMetadata,
  spec: KubernetesServiceSpec
) {
  def packageName: Option[String] = {
    metadata.labels match {
      case Some(labelMap) => labelMap.get(KubernetesService.nameLabel)
      case None => None
    }
  }

  def packageReleaseVersion: Option[ReleaseVersion] = {
    metadata.labels match {
      case Some(labelMap) => labelMap.get(KubernetesService.releaseLabel).map(ReleaseVersion)
      case None => None
    }
  }

  def packageVersion: Option[PackageDetailsVersion] = {
    metadata.labels match {
      case Some(labelMap) => labelMap.get(KubernetesService.versionLabel).map(PackageDetailsVersion)
      case None => None
    }    
  }

  def packageRepository: Option[String] = {
    metadata.labels match {
      case Some(labelMap) => labelMap.get(KubernetesService.repositoryLabel)
      case None => None
    }
  }
}

case class KubernetesServiceMetadata(
  name: String,
  namespace: String,
  selfLink: String,
  uid: String,
  creationTimestamp: String,
  labels: Option[Map[String, String]]
)

case class KubernetesServiceSpec(
  ports: List[KubernetesServicePorts],
  selector: Option[Map[String, String]],
  clusterIP: String
)

case class KubernetesServicePorts(
  name: Option[String],
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