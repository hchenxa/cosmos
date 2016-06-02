package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.{ListVersionsRequest, ListVersionsResponse}
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] class ListVersionsHandler(
  packageCache: PackageCollection
)(implicit
  decoder: DecodeRequest[ListVersionsRequest],
  encoder: Encoder[ListVersionsResponse]
) extends EndpointHandler(
  requestReader = RequestReaders.standard[ListVersionsRequest, ListVersionsResponse](
    accepts = MediaTypes.ListVersionsRequest,
    produces = MediaTypes.ListVersionsResponse
  )
) {

  override def apply(request: ListVersionsRequest)(implicit session: RequestSession): Future[ListVersionsResponse] = {
    packageCache
      .getPackageIndex(request.packageName)
      .map { packageInfo => ListVersionsResponse(packageInfo.versions) }
  }
}
