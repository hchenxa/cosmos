package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.{Authorization, MediaType, RequestSession}
import io.finch.{DecodeRequest, RequestReader}
import io.finch._

import scala.reflect.ClassTag

/** Allows [[io.finch.RequestReader]] instances to be defined independently of the
  * [[com.mesosphere.cosmos.handler.EndpointHandler]] instances that use them.
  *
  * Ideally this would just be an anonymous function, if it weren't for the implicit parameters.
  */
private[handler] trait RequestReaderBuilder[Request] {

  def build(accepts: MediaType, produces: MediaType)(implicit
    decoder: DecodeRequest[Request],
    requestClassTag: ClassTag[Request]
  ): RequestReader[(RequestSession, Request)]

}

private[handler] object RequestReaderBuilder {

  /** Builds a [[io.finch.RequestReader]] that does not parse the request body. */
  val noBody: RequestReaderBuilder[Unit] = new RequestReaderBuilder[Unit] {
    override def build(accepts: MediaType, produces: MediaType)(implicit
      decoder: DecodeRequest[Unit],
      requestClassTag: ClassTag[Unit]
    ): RequestReader[(RequestSession, Unit)] = {
      baseReader(produces).map((_, ()))
    }
  }

  /** Builds a [[io.finch.RequestReader]] that parses the request body as a `Request`. */
  def standard[Request]: RequestReaderBuilder[Request] = new RequestReaderBuilder[Request] {
    override def build(accepts: MediaType, produces: MediaType)(implicit
      decoder: DecodeRequest[Request],
      requestClassTag: ClassTag[Request]
    ): RequestReader[(RequestSession, Request)] = {
      for {
        reqSession <- baseReader(produces)
        _ <- header("Content-Type").as[MediaType].should(beTheExpectedType(accepts))
        req <- body.as[Request]
      } yield {
        reqSession -> req
      }
    }
  }

  private[this] def baseReader(produces: MediaType): RequestReader[RequestSession] = {
    for {
      _ <- header("Accept").as[MediaType].should(beTheExpectedType(produces))
      auth <- headerOption("Authorization").as[String]
    } yield {
      RequestSession(auth.map(Authorization(_)))
    }
  }

}
