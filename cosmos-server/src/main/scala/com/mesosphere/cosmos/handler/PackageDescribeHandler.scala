package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.{DescribeRequest, DescribeResponse}
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] class PackageDescribeHandler(
  packageCache: PackageCollection
)(implicit
  decoder: DecodeRequest[DescribeRequest],
  encoder: Encoder[DescribeResponse]
) extends EndpointHandler(
  requestReader = RequestReaders.standard[DescribeRequest, DescribeResponse](
    accepts = MediaTypes.DescribeRequest,
    produces = MediaTypes.DescribeResponse
  )
) {

  override def apply(request: DescribeRequest)(implicit session: RequestSession): Future[DescribeResponse] = {
    packageCache
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .map { packageFiles =>
        DescribeResponse(
          packageFiles.packageJson,
          packageFiles.marathonJsonMustache,
          packageFiles.commandJson,
          packageFiles.configJson,
          packageFiles.resourceJson
        )
      }
  }
}
