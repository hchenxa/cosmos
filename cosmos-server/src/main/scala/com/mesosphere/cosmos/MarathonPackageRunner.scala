package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonApp, MarathonError}
import com.mesosphere.cosmos.model.thirdparty.kubernetes.{KubernetesPod, KubernetesError}
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.RequestSession
import io.circe.parse.decode
import io.circe.Json

/** A [[com.mesosphere.cosmos.PackageRunner]] implementation for Marathon. */
final class MarathonPackageRunner(adminRouter: AdminRouter) extends PackageRunner {

  def launch(renderedConfig: Json)(implicit session: RequestSession): Future[MarathonApp] = {
    adminRouter.createApp(renderedConfig)
      .map { response =>
        response.status match {
          case Status.Conflict => throw PackageAlreadyInstalled()
          case status if (400 until 500).contains(status.code) =>
            decode[MarathonError](response.contentString) match {
              case Xor.Right(marathonError) =>
                throw new MarathonBadResponse(marathonError)
              case Xor.Left(parseError) =>
                throw new MarathonGenericError(status)
            }
          case status if (500 until 600).contains(status.code) =>
            throw MarathonBadGateway(status)
          case _ =>
            decode[MarathonApp](response.contentString) match {
              case Xor.Right(appResponse) => appResponse
              case Xor.Left(parseError) => throw new CirceError(parseError)
            }
        }
      }
  }
  
//Hack the function for kubernetes pod creation
 def launch_1(renderedConfig: Json)(implicit session: RequestSession): Future[KubernetesPod] = {
    adminRouter.createPod(renderedConfig)
      .map { response =>
        {
          val logger = org.slf4j.LoggerFactory.getLogger(getClass)        
          logger.info("The response in launch_1 was: {}", response.status)
          response.status match {
          case Status.Conflict => throw PackageAlreadyInstalled()
          case status if (400 until 500).contains(status.code) =>
            decode[MarathonError](response.contentString) match {
              case Xor.Right(marathonError) =>
                throw new MarathonBadResponse(marathonError)        
              case Xor.Left(parseError) =>
                throw new MarathonGenericError(status)
            }
          case status if (500 until 600).contains(status.code) =>
            throw MarathonBadGateway(status)
          case _ =>{
            logger.info("the resource.contentString was: {}", response.contentString)
            logger.info("The resource response was : {}", response)
            decode[KubernetesPod](response.contentString) match {
              case Xor.Right(appResponse) => appResponse
              case Xor.Left(parseError) => throw new CirceError(parseError)
            }
            }
          }
        }
        }
      }
}
