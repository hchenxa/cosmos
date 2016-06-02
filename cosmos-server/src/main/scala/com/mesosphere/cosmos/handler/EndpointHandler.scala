package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.twitter.util.Future
import io.circe.Json
import io.circe.syntax._
import io.finch._
import shapeless.HNil

private[cosmos] abstract class EndpointHandler[Request, Response, VersionedResponse](implicit
  codec: EndpointCodec[Request, Response, VersionedResponse]
) {

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

  private[cosmos] def forRoute(route: Endpoint[HNil]): Endpoint[Json] = {
    val endpoint = route ? codec.requestReader

    endpoint { context: EndpointContext[Request, Response, VersionedResponse] =>
      this(context.requestBody)(context.session).map { response =>
        val encodedResponse = context.responseFormatter(response)
          .asJson(codec.responseEncoder)

        Ok(encodedResponse).withContentType(Some(context.responseContentType.show))
      }
    }
  }

}
