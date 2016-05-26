package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model._
import io.circe.Encoder
import io.finch.{DecodeRequest, RequestReader}

import scala.reflect.ClassTag

/** Provides everything needed to decode requests and encode responses for an endpoint. */
final case class EndpointCodec[Request, Response](
  requestReader: RequestReader[EndpointContext[Request, Response]],
  responseEncoder: Encoder[Response]
)

object EndpointCodec {

  implicit def capabilitiesCodec(implicit
    encoder: Encoder[CapabilitiesResponse]
  ): EndpointCodec[Unit, CapabilitiesResponse] = {
    EndpointCodec(RequestReaders.noBody(producesOnly(MediaTypes.CapabilitiesResponse)), encoder)
  }

  implicit def packageListCodec(implicit
    decoder: DecodeRequest[ListRequest],
    encoder: Encoder[ListResponse]
  ): EndpointCodec[ListRequest, ListResponse] = standardCodec(
    accepts = MediaTypes.ListRequest,
    produces = producesOnly(MediaTypes.ListResponse)
  )

  implicit def packageListVersionsCodec(implicit
    decoder: DecodeRequest[ListVersionsRequest],
    encoder: Encoder[ListVersionsResponse]
  ): EndpointCodec[ListVersionsRequest, ListVersionsResponse] = standardCodec(
    accepts = MediaTypes.ListVersionsRequest,
    produces = producesOnly(MediaTypes.ListVersionsResponse)
  )

  implicit def packageDescribeCodec(implicit
    decoder: DecodeRequest[DescribeRequest],
    encoder: Encoder[DescribeResponse]
  ): EndpointCodec[DescribeRequest, DescribeResponse] = standardCodec(
    accepts = MediaTypes.DescribeRequest,
    produces = producesOnly(MediaTypes.DescribeResponse)
  )

  implicit def packageInstallCodec(implicit
    decoder: DecodeRequest[InstallRequest],
    encoder: Encoder[InstallResponse]
  ): EndpointCodec[InstallRequest, InstallResponse] = standardCodec(
    accepts = MediaTypes.InstallRequest,
    produces = producesOnly(MediaTypes.InstallResponse)
  )

  implicit def packageRenderCodec(implicit
    decoder: DecodeRequest[RenderRequest],
    encoder: Encoder[RenderResponse]
  ): EndpointCodec[RenderRequest, RenderResponse] = standardCodec(
    accepts = MediaTypes.RenderRequest,
    produces = producesOnly(MediaTypes.RenderResponse)
  )

  implicit def packageRepositoryAddCodec(implicit
    decoder: DecodeRequest[PackageRepositoryAddRequest],
    encoder: Encoder[PackageRepositoryAddResponse]
  ): EndpointCodec[PackageRepositoryAddRequest, PackageRepositoryAddResponse] = standardCodec(
    accepts = MediaTypes.PackageRepositoryAddRequest,
    produces = producesOnly(MediaTypes.PackageRepositoryAddResponse)
  )

  implicit def packageRepositoryDeleteCodec(implicit
    decoder: DecodeRequest[PackageRepositoryDeleteRequest],
    encoder: Encoder[PackageRepositoryDeleteResponse]
  ): EndpointCodec[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse] = standardCodec(
    accepts = MediaTypes.PackageRepositoryDeleteRequest,
    produces = producesOnly(MediaTypes.PackageRepositoryDeleteResponse)
  )

  implicit def packageRepositoryListCodec(implicit
    decoder: DecodeRequest[PackageRepositoryListRequest],
    encoder: Encoder[PackageRepositoryListResponse]
  ): EndpointCodec[PackageRepositoryListRequest, PackageRepositoryListResponse] = standardCodec(
    accepts = MediaTypes.PackageRepositoryListRequest,
    produces = producesOnly(MediaTypes.PackageRepositoryListResponse)
  )

  implicit def packageSearchCodec(implicit
    decoder: DecodeRequest[SearchRequest],
    encoder: Encoder[SearchResponse]
  ): EndpointCodec[SearchRequest, SearchResponse] = standardCodec(
    accepts = MediaTypes.SearchRequest,
    produces = producesOnly(MediaTypes.SearchResponse)
  )

  implicit def packageUninstallCodec(implicit
    decoder: DecodeRequest[UninstallRequest],
    encoder: Encoder[UninstallResponse]
  ): EndpointCodec[UninstallRequest, UninstallResponse] = standardCodec(
    accepts = MediaTypes.UninstallRequest,
    produces = producesOnly(MediaTypes.UninstallResponse)
  )

  private[this] def standardCodec[Request, Response](
    accepts: MediaType,
    produces: Seq[(MediaType, Response => Response)]
  )(implicit
    decoder: DecodeRequest[Request],
    encoder: Encoder[Response],
    requestClassTag: ClassTag[Request]
  ): EndpointCodec[Request, Response] = {
    EndpointCodec(RequestReaders.standard[Request, Response](accepts, produces), encoder)
  }

  private[this] def producesOnly[Response](
    mediaType: MediaType
  ): Seq[(MediaType, Response => Response)] = Seq((mediaType, identity))

}
