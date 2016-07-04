package com.mesosphere.cosmos.handler

import java.nio.charset.StandardCharsets
import java.util.Base64
import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.{KubernetesInstallation, InstalledPackageInformation, KubernetesListRequest, KubernetesListResponse}
import com.mesosphere.cosmos.repository.Repository
import com.mesosphere.universe.{PackageDetails, ReleaseVersion}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

final class KubernetesListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Option[Repository]]
)(implicit
  requestDecoder: DecodeRequest[KubernetesListRequest],
  responseEncoder: Encoder[KubernetesListResponse]
) extends EndpointHandler[KubernetesListRequest, KubernetesListResponse] {

  val accepts = MediaTypes.KubernetesListRequest
  val produces = MediaTypes.KubernetesListResponse

  override def apply(request: KubernetesListRequest)(implicit session: RequestSession): Future[KubernetesListResponse] = {
    val logger = org.slf4j.LoggerFactory.getLogger(getClass)
    logger.info(s"List request: $request.toString()")

    adminRouter.listServices().flatMap { svcs =>
      Future.collect {
        svcs.items.map { svc =>
          (svc.packageReleaseVersion, svc.packageName, svc.packageRepository) match {
            case (Some(releaseVersion), Some(packageName), Some(repositoryUri))
              if request.packageName.forall(_ == packageName) && request.service.forall(_ == svc.metadata.name) && request.namespace.forall(_ == svc.metadata.namespace) =>
                logger.info(s"Get request Kubernetes service: $svc.metadata.name")
                
                installedPackageInformation(packageName, releaseVersion, repositoryUri)
                  .map {
                    case Some(resolvedFromRepo) => resolvedFromRepo
                    case None => throw new kubernetesGetPackageDetailError(s"Failed to get package information of $packageName")
                  }
                .map(packageInformation => Some(KubernetesInstallation(svc.metadata.name, svc.metadata.namespace, packageInformation)))
            case _ =>
              // TODO: log debug message when one of them is Some.
              Future.value(None)
          }
        }
      } map { installation =>
        KubernetesListResponse(installation.flatten)
      }
    }
  }

  private[this] def installedPackageInformation(
    packageName: String,
    releaseVersion: ReleaseVersion,
    repositoryUri: Uri
  ): Future[Option[InstalledPackageInformation]] = {
    repositories(repositoryUri)
      .flatMap {
        case Some(repository) =>
          repository.getPackageByReleaseVersion(packageName, releaseVersion)
            .map { packageFiles =>
              Some(InstalledPackageInformation(packageFiles.packageJson, packageFiles.resourceJson))
            }
        case _ => Future.value(None)
      }
  }

}
