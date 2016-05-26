package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaType, MediaTypes, RequestSession}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] final class PackageRepositoryListHandler(
  sourcesStorage: PackageSourcesStorage
)(implicit
  decoder: DecodeRequest[PackageRepositoryListRequest],
  encoder: Encoder[PackageRepositoryListResponse]
) extends EndpointHandler[PackageRepositoryListRequest, PackageRepositoryListResponse](
  RequestReaders.standard(
    accepts = MediaTypes.PackageRepositoryListRequest,
    produces = EndpointHandler.producesOnly(MediaTypes.PackageRepositoryListResponse)
  )
) {
  override def apply(req: PackageRepositoryListRequest)(implicit session: RequestSession): Future[PackageRepositoryListResponse] = {
    sourcesStorage.read().map(PackageRepositoryListResponse(_))
  }
}
