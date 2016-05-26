package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.{CapabilitiesResponse, Capability}
import com.twitter.util.Future

private[cosmos] final class CapabilitiesHandler(implicit
  codec: EndpointCodec[Unit, CapabilitiesResponse]
) extends EndpointHandler {

  private[this] val response = CapabilitiesResponse(List(Capability("PACKAGE_MANAGEMENT")))

  override def apply(v1: Unit)(implicit session: RequestSession): Future[CapabilitiesResponse] = {
    Future.value(response)
  }
}
