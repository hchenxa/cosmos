package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.twitter.finagle.http.Method
import com.twitter.util.Future
import io.circe.Json
import io.circe.syntax._
import io.finch._
import shapeless.HNil

private[cosmos] abstract class EndpointHandler[Request, Response](implicit
  codec: EndpointCodec[Request, Response]
) {

  import EndpointHandler._

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

  private[cosmos] def route(method: Method, path: String*): Endpoint[Json] = {
    val endpointPath = path.foldLeft[Endpoint[HNil]](/)(_ / _)

    endpoint(method)(endpointPath ? codec.requestReader) {
      context: EndpointContext[Request, Response] =>

        this(context.requestBody)(context.session).map { response =>
          val encodedResponse = context.responseFormatter(response)
            .asJson(codec.responseEncoder)

          Ok(encodedResponse).withContentType(Some(context.responseContentType.show))
        }
    }
  }

  /** Temporary method for determining if the tests are written correctly. */
  private[cosmos] def testRoute(method: Method, path: String*): Endpoint[Json] = {
    val endpointPath = path.foldLeft[Endpoint[HNil]](/)(_ / _)

    endpoint(method)(endpointPath ? codec.requestReader) {
      context: EndpointContext[Request, Response] =>

        this(context.requestBody)(context.session).map { response =>
          val encodedResponse = context.responseFormatter(response)
            .asJson(codec.responseEncoder)

          NoContent(encodedResponse).withContentType(Some(context.responseContentType.show))
        }
    }
  }

}

object EndpointHandler {

  private def endpoint[A](method: Method): Endpoint[A] => Endpoint[A] = {
    method match {
      case Method.Get     => get
      case Method.Post    => post
      case Method.Put     => put
      case Method.Head    => head
      case Method.Patch   => patch
      case Method.Delete  => delete
      case Method.Trace   => trace
      case Method.Connect => connect
      case Method.Options => options
      case _              => throw new IllegalArgumentException(s"Unknown HTTP method: $method")
    }
  }

}
