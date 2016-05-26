package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.{ListVersionsRequest, ListVersionsResponse}
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future

private[cosmos] class ListVersionsHandler(packageCache: PackageCollection)(implicit
  codec: EndpointCodec[ListVersionsRequest, ListVersionsResponse]
) extends EndpointHandler {

  override def apply(request: ListVersionsRequest)(implicit session: RequestSession): Future[ListVersionsResponse] = {
    packageCache
      .getPackageIndex(request.packageName)
      .map { packageInfo => ListVersionsResponse(packageInfo.versions) }
  }
}
