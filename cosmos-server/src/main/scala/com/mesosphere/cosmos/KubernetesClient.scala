package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonAppResponse, MarathonAppsResponse}
import com.mesosphere.cosmos.model.thirdparty.kubernetes._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.circe.Json
import org.jboss.netty.handler.codec.http.HttpMethod

class KubernetesClient(
  kubernetesUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(kubernetesUri) {

  def createRC(appJson: Json, namespace: String)(implicit session: RequestSession): Future[Response] = {
    logger.info("create pods with json file:{}", appJson)
    val uri = s"api/v1/namespaces/$namespace/replicationcontrollers"
    client(post(uri, appJson)).map {
      response => {
          logger.info("response status:{}", response.status)
          response
        }
      }
  }
  
  def createService(appJson: Json, namespace: String)(implicit session: RequestSession): Future[Response] = {
    logger.info("create pods with json file:{}", appJson)
    val uri = s"api/v1/namespaces/$namespace/services"
    client(post(uri, appJson)).map {
      response => {
          logger.info("response status:{}", response.status)
          response
        }
      }
  }

  def getRC(rc: String, namespace: String)(implicit session: RequestSession): Future[KubernetesRCResponse] = {
    val uri = s"api/v1/namespaces/$namespace/replicationcontrollers"
    client(get(uri)).map { response =>
      response.status match {
        case Status.Ok => decodeJsonTo[KubernetesRCResponse](response)
        case Status.NotFound => throw KubernetesRCNotFound(rc)
        case s: Status => throw GenericHttpError(HttpMethod.GET, uri, s)
      }
    }
  }
    
  def getService(service: String, namespace: String)(implicit session: RequestSession): Future[KubernetesServiceResponse] = {
    val uri = s"api/v1/namespaces/$namespace/services"
    client(get(uri)).map { response =>
      response.status match {
        case Status.Ok => decodeJsonTo[KubernetesServiceResponse](response)
        case Status.NotFound => throw KubernetesServiceNotFound(service)
        case s: Status => throw GenericHttpError(HttpMethod.GET, uri, s)
      }
    }
  }
    
  def listRCs()(implicit session: RequestSession): Future[KubernetesRCsResponse] = {
    val uri = s"api/v1/replicationcontrollers"
    client(get(uri)).flatMap(decodeTo[KubernetesRCsResponse](HttpMethod.GET, uri, _))
  }
  
  def listServices()(implicit session: RequestSession): Future[KubernetesServicesResponse] = {
    val uri = s"api/v1/services"
    client(get(uri)).flatMap(decodeTo[KubernetesServicesResponse](HttpMethod.GET, uri, _))
  }

  def deleteRC(rcName: String, namespace: String)(implicit session: RequestSession): Future[Response] = {
    val uri = s"api/v1/namespaces/$namespace/replicationcontrollers/$rcName"
    client(delete(uri))
  }
  
  def deleteService(serviceName: String, namespace: String)(implicit session: RequestSession): Future[Response] = {
    val uri = s"api/v1/namespaces/$namespace/services/$serviceName"
    client(delete(uri))
  }
}
