package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.{CapabilitiesResponse, Capability}
import com.twitter.util.{Future, Try}
import io.circe.Encoder
import io.finch._

class CapabilitiesHandler private(implicit
  decodeRequest: DecodeRequest[Unit],
  encoder: Encoder[CapabilitiesResponse]
) extends EndpointHandler[Unit, CapabilitiesResponse](
  RequestReaders.noBody(EndpointHandler.producesOnly(MediaTypes.CapabilitiesResponse))
) {

  private[this] val response = CapabilitiesResponse(List(Capability("PACKAGE_MANAGEMENT")))

  override def apply(v1: Unit)(implicit session: RequestSession): Future[CapabilitiesResponse] = {
    Future.value(response)
  }
}

object CapabilitiesHandler {
  def apply()(implicit encoder: Encoder[CapabilitiesResponse]): CapabilitiesHandler = {
    implicit val decoder = DecodeRequest.instance[Unit](_ => Try(()))
    new CapabilitiesHandler()
  }
}
