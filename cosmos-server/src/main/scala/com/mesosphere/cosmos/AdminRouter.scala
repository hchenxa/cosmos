package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.thirdparty.adminrouter.DcosVersion
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonAppResponse, MarathonAppsResponse}
import com.mesosphere.cosmos.model.thirdparty.kubernetes._
import com.mesosphere.cosmos.model.thirdparty.mesos.master._
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.circe.Json

class AdminRouter(
  adminRouterClient: AdminRouterClient,
  marathon: MarathonClient,
  kubernetes: KubernetesClient,
  mesos: MesosMasterClient
) {


  // Marathon actions.
  def createApp(appJson: Json)(implicit session: RequestSession): Future[Response] = marathon.createApp(appJson)

  def getAppOption(appId: AppId)(implicit session: RequestSession): Future[Option[MarathonAppResponse]] = marathon.getAppOption(appId)

  def getApp(appId: AppId)(implicit session: RequestSession): Future[MarathonAppResponse] = marathon.getApp(appId)

  def listApps()(implicit session: RequestSession): Future[MarathonAppsResponse] = marathon.listApps()

  def deleteApp(appId: AppId, force: Boolean = false)(implicit session: RequestSession): Future[Response] = marathon.deleteApp(appId, force)

  // Mesos actions.
  def tearDownFramework(frameworkId: String)(implicit session: RequestSession): Future[MesosFrameworkTearDownResponse] = mesos.tearDownFramework(frameworkId)

  def getMasterState(frameworkName: String)(implicit session: RequestSession): Future[MasterState] = mesos.getMasterState(frameworkName)

  def getDcosVersion()(implicit session: RequestSession): Future[DcosVersion] = adminRouterClient.getDcosVersion()

  // Kubernetes actions
  def createRC(rcJson: Json, namespace: String)(implicit session: RequestSession): Future[Response] = kubernetes.createRC(rcJson, namespace)

  def getRC(rc: String, namespace: String)(implicit session: RequestSession): Future[KubernetesRCResponse] = kubernetes.getRC(rc, namespace)

  def listRCs()(implicit session: RequestSession): Future[KubernetesRCsResponse] = kubernetes.listRCs()

  def deleteRC(rc: String, namespace: String)(implicit session: RequestSession): Future[Response] = kubernetes.deleteRC(rc, namespace)

  def createService(serviceJson: Json, namespace: String)(implicit session: RequestSession): Future[Response] = kubernetes.createService(serviceJson, namespace)

  def getService(service: String, namespace: String)(implicit session: RequestSession): Future[KubernetesServiceResponse] = kubernetes.getService(service, namespace)

  def listServices()(implicit session: RequestSession): Future[KubernetesServicesResponse] = kubernetes.listServices()

  def deleteService(service: String, namespace: String)(implicit session: RequestSession): Future[Response] = kubernetes.deleteService(service, namespace)

}
