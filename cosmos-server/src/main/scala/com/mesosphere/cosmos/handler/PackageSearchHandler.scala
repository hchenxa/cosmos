package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageCollection
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] final class PackageSearchHandler(
  packageCache: PackageCollection
)(implicit
  decoder: DecodeRequest[SearchRequest],
  encoder: Encoder[SearchResponse]
) extends EndpointHandler(
  requestReader = RequestReaders.standard[SearchRequest, SearchResponse](
    accepts = MediaTypes.SearchRequest,
    produces = MediaTypes.SearchResponse
  )
) {

  override def apply(request: SearchRequest)(implicit session: RequestSession): Future[SearchResponse] = {
    packageCache.search(request.query) map { packages =>
      val sortedPackages = packages.sortBy(p => (!p.selected.getOrElse(false), p.name.toLowerCase))
      SearchResponse(sortedPackages)
    }
  }
}
