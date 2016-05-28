package com.mesosphere.cosmos.handler

import cats.syntax.option._
import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.MediaTypeOps.mediaTypeToMediaTypeOps
import com.mesosphere.cosmos.http.{Authorization, MediaType, MediaTypes, RequestSession}
import io.finch._

import scala.reflect.ClassTag

object RequestReaders {

  /** Builds a [[io.finch.RequestReader]] that does not parse the request body. */
  def noBody[Res](
    produces: Seq[(MediaType, Res => Res)]
  ): RequestReader[EndpointContext[Unit, Res]] = {
    baseReader(produces).map { case (session, responseFormatter, responseContentType) =>
      EndpointContext((), session, responseFormatter, responseContentType)
    }
  }

  /** Builds a [[io.finch.RequestReader]] that parses the request body. */
  def standard[Req, Res](accepts: MediaType, produces: Seq[(MediaType, Res => Res)])(implicit
    decoder: DecodeRequest[Req],
    requestClassTag: ClassTag[Req]
  ): RequestReader[EndpointContext[Req, Res]] = {
    for {
      (reqSession, responseFormatter, responseContentType) <- baseReader(produces)
      _ <- header("Content-Type").as[MediaType].should(beTheExpectedType(accepts))
      req <- body.as[Req]
    } yield {
      EndpointContext(req, reqSession, responseFormatter, responseContentType)
    }
  }

  private[this] def baseReader[Res](
    produces: Seq[(MediaType, Res => Res)]
  ): RequestReader[(RequestSession, Res => Res, MediaType)] = {
    for {
      (responseContentType, responseFormatter) <- header("Accept")
        .as[MediaType]
        .convert { accept =>
          produces.find { case (supported, _) => supported.isCompatibleWith(accept) }
            .toRightXor(s"should match one of: ${produces.map(_._1.show).mkString(", ")}")
        }
      auth <- headerOption("Authorization").as[String]
    } yield {
      (RequestSession(auth.map(Authorization(_))), responseFormatter, responseContentType)
    }
  }

  private[handler] def testBaseReader[Res](
    produces: Seq[(MediaType, Res => Res)]
  ): RequestReader[(RequestSession, Res => Res, MediaType)] = {
    for {
      (responseContentType, responseFormatter) <- header("Accept")
        .as[MediaType]
        .convert { accept =>
          produces.find { case (supported, _) => supported.isCompatibleWith(accept) }
            .toRightXor(s"should match one of: ${produces.map(_._1.show).mkString(", ")}")
        }
      auth <- headerOption("Authorization")
    } yield {
      (RequestSession(auth.map(Authorization(_))), responseFormatter, responseContentType)
    }
  }

}
