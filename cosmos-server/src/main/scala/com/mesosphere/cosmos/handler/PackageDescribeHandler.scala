package com.mesosphere.cosmos.handler

import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest
import com.mesosphere.cosmos.http.{MediaType, MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.{DescribeRequest, DescribeResponse}
import com.mesosphere.cosmos.repository.PackageCollection

private[cosmos] class PackageDescribeHandler(
  packageCache: PackageCollection
)(implicit
  bodyDecoder: DecodeRequest[DescribeRequest],
  encoder: Encoder[DescribeResponse]
) extends EndpointHandler[DescribeRequest, DescribeResponse](RequestReaders.standard(
  accepts = MediaTypes.DescribeRequest,
  produces = EndpointHandler.producesOnly(MediaTypes.DescribeResponse)
)) {
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
