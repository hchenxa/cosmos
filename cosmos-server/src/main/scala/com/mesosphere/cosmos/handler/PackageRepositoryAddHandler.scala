package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.UnsupportedRepositoryUri
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.PackageRepository
import com.mesosphere.cosmos.model.PackageRepositoryAddRequest
import com.mesosphere.cosmos.model.PackageRepositoryAddResponse
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.twitter.util.Future

private[cosmos] final class PackageRepositoryAddHandler(
  sourcesStorage: PackageSourcesStorage
)(implicit
  codec: EndpointCodec[PackageRepositoryAddRequest, PackageRepositoryAddResponse, PackageRepositoryAddResponse]
) extends EndpointHandler {

  override def apply(
    request: PackageRepositoryAddRequest
  )(implicit session: RequestSession): Future[PackageRepositoryAddResponse] = {
    request.uri.scheme match {
      case Some("http") | Some("https") =>
        sourcesStorage.add(
          request.index,
          PackageRepository(request.name, request.uri)
        ) map { sources =>
          PackageRepositoryAddResponse(sources)
        }
      case _ => throw UnsupportedRepositoryUri(request.uri)
    }
  }
}
