package com.mesosphere.cosmos.handler

import cats.syntax.option._
import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.MediaTypeOps.mediaTypeToMediaTypeOps
import com.mesosphere.cosmos.http.{Authorization, MediaType, MediaTypes, RequestSession}
import io.finch._

import scala.reflect.ClassTag

object RequestReaders {

  def noBody[Res](produces: MediaType): RequestReader[EndpointContext[Unit, Res, Res]] = {
    noBody(producesOnly[Res](produces))
  }

  def standard[Req, Res](accepts: MediaType, produces: MediaType)(implicit
    decoder: DecodeRequest[Req],
    reqClassTag: ClassTag[Req]
  ): RequestReader[EndpointContext[Req, Res, Res]] = {
    standard(accepts, producesOnly[Res](produces))
  }

  def noBody[Res, VRes](
    produces: Seq[(MediaType, Res => VRes)]
  ): RequestReader[EndpointContext[Unit, Res, VRes]] = {
    baseReader(produces).map { case (session, responseFormatter, responseContentType) =>
      EndpointContext((), session, responseFormatter, responseContentType)
    }
  }

  def standard[Req, Res, VRes](accepts: MediaType, produces: Seq[(MediaType, Res => VRes)])(implicit
    decoder: DecodeRequest[Req],
    requestClassTag: ClassTag[Req]
  ): RequestReader[EndpointContext[Req, Res, VRes]] = {
    for {
      (reqSession, responseFormatter, responseContentType) <- baseReader(produces)
      _ <- header("Content-Type").as[MediaType].should(beTheExpectedType(accepts))
      req <- body.as[Req]
    } yield {
      EndpointContext(req, reqSession, responseFormatter, responseContentType)
    }
  }

  private[this] def baseReader[Res, VRes](
    produces: Seq[(MediaType, Res => VRes)]
  ): RequestReader[(RequestSession, Res => VRes, MediaType)] = {
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

  private[this] def producesOnly[Response](
    mediaType: MediaType
  ): Seq[(MediaType, Response => Response)] = Seq((mediaType, identity))

}
