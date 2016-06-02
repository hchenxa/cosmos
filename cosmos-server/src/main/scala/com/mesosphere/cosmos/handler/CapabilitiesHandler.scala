package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.{CapabilitiesResponse, Capability}
import com.twitter.util.Future
import io.circe.Encoder

private[cosmos] final class CapabilitiesHandler(implicit
  encoder: Encoder[CapabilitiesResponse]
) extends EndpointHandler(
  requestReader = RequestReaders.noBody[CapabilitiesResponse](
    produces = MediaTypes.CapabilitiesResponse
  )
) {

  private[this] val response = CapabilitiesResponse(List(Capability("PACKAGE_MANAGEMENT")))

  override def apply(v1: Unit)(implicit session: RequestSession): Future[CapabilitiesResponse] = {
    Future.value(response)
  }
}
