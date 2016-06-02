package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.twitter.util.Future
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.finch._
import shapeless.HNil

private[cosmos] abstract class EndpointHandler[Request, Response, VersionedResponse](
  requestReader: RequestReader[EndpointContext[Request, Response, VersionedResponse]]
)(implicit responseEncoder: Encoder[VersionedResponse]) {

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

  private[cosmos] def forRoute(route: Endpoint[HNil]): Endpoint[Json] = {
    val endpoint = route ? requestReader

    endpoint { context: EndpointContext[Request, Response, VersionedResponse] =>
      this(context.requestBody)(context.session).map { response =>
        val encodedResponse = context.responseFormatter(response).asJson

        Ok(encodedResponse).withContentType(Some(context.responseContentType.show))
      }
    }
  }

}
