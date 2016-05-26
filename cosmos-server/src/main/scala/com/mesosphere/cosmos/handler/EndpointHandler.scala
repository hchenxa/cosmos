package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaType, RequestSession}
import com.twitter.finagle.http.Method
import com.twitter.util.Future
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.finch._
import shapeless.HNil

import scala.reflect.ClassTag

private[cosmos] abstract class EndpointHandler[Request, Response](
  accepts: MediaType,
  produces: Seq[(MediaType, Response => Response)],
  readerBuilder: RequestReaderBuilder[Request, Response] = RequestReaderBuilder.standard[Request, Response]
)(implicit
  decoder: DecodeRequest[Request],
  requestClassTag: ClassTag[Request],
  encoder: Encoder[Response],
  responseClassTag: ClassTag[Response]
) {

  import EndpointHandler._

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

  private[this] type Context = EndpointContext[Request, Response]

  private[cosmos] def route(method: Method, path: String*): Endpoint[Json] = {
    endpoint(method)(path.foldLeft[Endpoint[HNil]](/)(_ / _) ? reader) { context: Context =>
      this(context.requestBody)(context.session).map { response =>
        Ok(context.responseFormatter(response).asJson)
          .withContentType(Some(context.responseContentType.show))
      }
    }
  }

  private[this] val reader: RequestReader[Context] = {
    readerBuilder.build(accepts, produces)
  }

}

object EndpointHandler {

  def producesOnly[Response](mediaType: MediaType): Seq[(MediaType, Response => Response)] = {
    Seq((mediaType, identity))
  }

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
