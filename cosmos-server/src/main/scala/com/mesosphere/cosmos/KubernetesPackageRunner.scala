package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.model.thirdparty.kubernetes._
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.RequestSession
import io.circe.parse.decode
import io.circe.{Json, HCursor}
import scala.collection.mutable.ListBuffer

/** A [[com.mesosphere.cosmos.PackageRunner]] implementation for Kubernetes. */
final class KubernetesPackageRunner(adminRouter: AdminRouter) extends PackageRunner[KubernetesService] {

  val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  
  def launch(renderedConfig: Json, ns: Option[String] = None)(implicit session: RequestSession): Future[KubernetesService] = {
    logger.info("The request json is: {}", renderedConfig)
    
    import KubernetesPackageRunner._
    
    val namespace = ns.getOrElse("default")
    val (rcs, services) = getKubernetesObjects(renderedConfig)
    
    if (!rcs.isEmpty && !services.isEmpty) {
      logger.info("Request to create RC json: {}", rcs)
      rcs.map { rc =>
        adminRouter.createRC(rc, namespace)
          .map { response =>
            {
              logger.info("the resource.contentString was: {}", response.contentString)
              logger.info("The resource response was : {}", response)
              response.status match {
              case Status.Conflict => throw PackageAlreadyInstalled()
              case status if (400 until 500).contains(status.code) =>
                decode[KubernetesError](response.contentString) match {
                  case Xor.Right(kubernetesError) => {
                    logger.error(s"Kubernetes error: $kubernetesError.toString()")
                    throw new KubernetesBadResponse(kubernetesError)
                  }
                  case Xor.Left(parseError) => {
                    logger.error(s"Kubernetes parseError: $parseError.toString()")
                    throw new KubernetesGenericError(status)
                  }
                }
              case status if (500 until 600).contains(status.code) => {
                throw KubernetesBadGateway(status)
              }
              case _ =>{
                decode[KubernetesRC](response.contentString) match {
                  case Xor.Right(rcResponse) => rcResponse
                  case Xor.Left(parseError) => throw new CirceError(parseError)
                }
                }
              }
            }
          }
      }
      
      logger.info("Request to create service json: {}", services)
      val responses = services.map { service =>
        adminRouter.createService(service, namespace)
          .map { response =>
            {
              logger.info("the resource.contentString was: {}", response.contentString)
              logger.info("The resource response was : {}", response)
              response.status match {
              case Status.Conflict => throw PackageAlreadyInstalled()
              case status if (400 until 500).contains(status.code) =>
                decode[KubernetesError](response.contentString) match {
                  case Xor.Right(kubernetesError) =>
                    throw new KubernetesBadResponse(kubernetesError)
                  case Xor.Left(parseError) =>
                    throw new KubernetesGenericError(status)
                }
              case status if (500 until 600).contains(status.code) =>
                throw KubernetesBadGateway(status)
              case _ => {
                decode[KubernetesService](response.contentString) match {
                  case Xor.Right(svcResponse) => svcResponse
                  case Xor.Left(parseError) => throw new CirceError(parseError)
                }
                }
              }
            }
          }
      }
      
      // Currently assume only one K8s service was defined in Kubernetes package file.
      responses.apply(0)
    } else {
      throw new kubernetesInvalidPackageError("The package file is invalid")
    }
    
    //TODO(liqlin): Handle dependences between Services/Apps.
  }
}

private[cosmos] object KubernetesPackageRunner {
  private[cosmos] def getKubernetesObjects(renderedConfig: Json): (List[Json], List[Json]) = {
    val apps = renderedConfig.hcursor.downField("apps").as[List[Json]] match {
      case Xor.Right(obj) => obj
      case Xor.Left(decodingFailure) => throw new CirceError(decodingFailure)
    }
    
    val rcs = apps.filter((json: Json) => {
      json.hcursor.downField("kind").as[String] match {
        case Xor.Right(kind) => {
          kind == "ReplicationController"
        }
        case Xor.Left(decodingFailure) => false
      }
    })
    
    val services = apps.filter((json: Json) => {
      json.hcursor.downField("kind").as[String] match {
        case Xor.Right(kind) => {
          kind == "Service"
        }
        case Xor.Left(decodingFailure) => false
      }
    })
    
    return (rcs, services)
  }
}