package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] final class PackageRenderHandler(
  packageCache: PackageCollection
)(implicit
  decoder: DecodeRequest[RenderRequest],
  encoder: Encoder[RenderResponse]
) extends EndpointHandler(
  requestReader = RequestReaders.standard[RenderRequest, RenderResponse](
    accepts = MediaTypes.RenderRequest,
    produces = MediaTypes.RenderResponse
  )
) {

  import PackageInstallHandler._

  override def apply(request: RenderRequest)(implicit session: RequestSession): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .map { packageFiles =>
        RenderResponse(
          preparePackageConfig(request.appId, request.options, packageFiles)
        )
      }
  }
}
