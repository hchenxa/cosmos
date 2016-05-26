package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.{DescribeRequest, DescribeResponse}
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future

private[cosmos] class PackageDescribeHandler(packageCache: PackageCollection)(implicit
  codec: EndpointCodec[DescribeRequest, DescribeResponse]
) extends EndpointHandler {

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
