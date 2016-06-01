package com.mesosphere.cosmos

import java.nio.file.Path

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.{PackageSourcesStorage, UniverseClient, ZooKeeperStorage}
import com.netaporter.uri.Uri
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.util.Try
import io.circe.Json
import io.finch._
import io.finch.circe._
import io.github.benwhitehead.finch.FinchServer
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry

private[cosmos] final class Cosmos(
  uninstallHandler: EndpointHandler[UninstallRequest, UninstallResponse, UninstallResponse],
  packageInstallHandler: EndpointHandler[InstallRequest, InstallResponse, InstallResponse],
  packageRenderHandler: EndpointHandler[RenderRequest, RenderResponse, RenderResponse],
  packageSearchHandler: EndpointHandler[SearchRequest, SearchResponse, SearchResponse],
  packageDescribeHandler: EndpointHandler[DescribeRequest, DescribeResponse, DescribeResponse],
  packageListVersionsHandler: EndpointHandler[ListVersionsRequest, ListVersionsResponse, ListVersionsResponse],
  listHandler: EndpointHandler[ListRequest, ListResponse, ListResponse],
  listRepositoryHandler: EndpointHandler[PackageRepositoryListRequest, PackageRepositoryListResponse, PackageRepositoryListResponse],
  addRepositoryHandler: EndpointHandler[PackageRepositoryAddRequest, PackageRepositoryAddResponse, PackageRepositoryAddResponse],
  deleteRepositoryHandler: EndpointHandler[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse, PackageRepositoryDeleteResponse],
  capabilitiesHandler: CapabilitiesHandler
)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {
  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

  val packageInstall: Endpoint[Json] = {
    packageInstallHandler.route(Method.Post, "package", "install")
  }

  val packageUninstall: Endpoint[Json] = {
    uninstallHandler.route(Method.Post, "package", "uninstall")
  }

  val packageDescribe: Endpoint[Json] = {
    packageDescribeHandler.route(Method.Post, "package", "describe")
  }

  val packageRender: Endpoint[Json] = {
    packageRenderHandler.route(Method.Post, "package", "render")
  }

  val packageListVersions: Endpoint[Json] = {
    packageListVersionsHandler.route(Method.Post, "package", "list-versions")
  }

  val packageSearch: Endpoint[Json] = {
    packageSearchHandler.route(Method.Post, "package", "search")
  }

  val packageList: Endpoint[Json] = {
    listHandler.route(Method.Post, "package", "list")
  }

  val capabilities: Endpoint[Json] = {
    capabilitiesHandler.route(Method.Get, "capabilities")
  }

  val packageListSources: Endpoint[Json] = {
    listRepositoryHandler.route(Method.Post, "package", "repository", "list")
  }

  val packageAddSource: Endpoint[Json] = {
    addRepositoryHandler.route(Method.Post, "package", "repository", "add")
  }

  val packageDeleteSource: Endpoint[Json] = {
    deleteRepositoryHandler.route(Method.Post, "package", "repository", "delete")
  }

  val service: Service[Request, Response] = {
    val stats = statsReceiver.scope("errorFilter")

    (packageInstall
      :+: packageRender
      :+: packageDescribe
      :+: packageSearch
      :+: packageUninstall
      :+: packageListVersions
      :+: packageList
      :+: packageListSources
      :+: packageAddSource
      :+: packageDeleteSource
      :+: capabilities
    )
      .handle {
        case ce: CosmosError =>
          stats.counter(s"definedError/${sanitiseClassName(ce.getClass)}").incr()
          val output = Output.failure(ce, ce.status).withContentType(Some(MediaTypes.ErrorResponse.show))
          ce.getHeaders.foldLeft(output) { case (out, kv) => out.withHeader(kv) }
        case fe: io.finch.Error =>
          stats.counter(s"finchError/${sanitiseClassName(fe.getClass)}").incr()
          Output.failure(fe, Status.BadRequest).withContentType(Some(MediaTypes.ErrorResponse.show))
        case e: Exception if !e.isInstanceOf[io.finch.Error] =>
          stats.counter(s"unhandledException/${sanitiseClassName(e.getClass)}").incr()
          logger.warn("Unhandled exception: ", e)
          Output.failure(e, Status.InternalServerError).withContentType(Some(MediaTypes.ErrorResponse.show))
        case t: Throwable if !t.isInstanceOf[io.finch.Error] =>
          stats.counter(s"unhandledThrowable/${sanitiseClassName(t.getClass)}").incr()
          logger.warn("Unhandled throwable: ", t)
          Output.failure(new Exception(t), Status.InternalServerError).withContentType(Some(MediaTypes.ErrorResponse.show))
      }
      .toService
  }

  /**
    * Removes characters from class names that are disallowed by some metrics systems.
    *
    * @param clazz the class whose name is to be santised
    * @return The name of the specified class with all "illegal characters" replaced with '.'
    */
  private[this] def sanitiseClassName(clazz: Class[_]): String = {
    clazz.getName.replaceAllLiterally("$", ".")
  }

}

object Cosmos extends FinchServer {
  def service = {
    implicit val stats = statsReceiver.scope("cosmos")
    import com.netaporter.uri.dsl._

    HttpProxySupport.configureProxySupport()

    val ar = Try(dcosUri())
      .map { dh =>
        val dcosHost: String = Uris.stripTrailingSlash(dh)
        logger.info("Connecting to DCOS Cluster at: {}", dcosHost)
        val mar: Uri = dcosHost / "marathon"
        val mesos: Uri = dcosHost / "mesos"
        mar -> mesos
      }
      .handle {
        case _: IllegalArgumentException =>
          val mar: Uri = marathonUri().toStringRaw
          val master: Uri = mesosMasterUri().toStringRaw
          logger.info("Connecting to Marathon at: {}", mar)
          logger.info("Connecting to Mesos master at: {}", master)
          mar -> master
      }
      .flatMap { case (marathon, mesosMaster) =>
        Trys.join(
          Services.marathonClient(marathon).map { marathon -> _ },
          Services.mesosClient(mesosMaster).map { mesosMaster -> _ }
        )
      }
      .map { case (marathon, mesosMaster) =>
        new AdminRouter(
          new MarathonClient(marathon._1, marathon._2),
          new MesosMasterClient(mesosMaster._1, mesosMaster._2)
        )
      }

    val boot = ar map { adminRouter =>
      val dd = dataDir()
      logger.info("Using {} for data directory", dd)

      val zkUri = zookeeperUri()
      logger.info("Using {} for the zookeeper connection", zkUri)

      val marathonPackageRunner = new MarathonPackageRunner(adminRouter)

      val zkRetryPolicy = new ExponentialBackoffRetry(1000, 3)
      val zkClient = CuratorFrameworkFactory.builder()
        .namespace(zkUri.path.stripPrefix("/"))
        .connectString(zkUri.connectString)
        .retryPolicy(zkRetryPolicy)
        .build

      // Start the client and close it on exit
      zkClient.start()
      onExit {
        zkClient.close()
      }

      val sourcesStorage = new ZooKeeperStorage(zkClient)()

      val cosmos = Cosmos(adminRouter, marathonPackageRunner, sourcesStorage, UniverseClient(), dd)
      cosmos.service
    }
    boot.get
  }

  private[cosmos] def apply(
    adminRouter: AdminRouter,
    packageRunner: PackageRunner,
    sourcesStorage: PackageSourcesStorage,
    universeClient: UniverseClient,
    dataDir: Path
  )(implicit statsReceiver: StatsReceiver = NullStatsReceiver): Cosmos = {

    val repositories = new MultiRepository(sourcesStorage, dataDir, universeClient)

    new Cosmos(
      new UninstallHandler(adminRouter, repositories),
      new PackageInstallHandler(repositories, packageRunner),
      new PackageRenderHandler(repositories),
      new PackageSearchHandler(repositories),
      new PackageDescribeHandler(repositories),
      new ListVersionsHandler(repositories),
      new ListHandler(adminRouter, uri => repositories.getRepository(uri)),
      new PackageRepositoryListHandler(sourcesStorage),
      new PackageRepositoryAddHandler(sourcesStorage),
      new PackageRepositoryDeleteHandler(sourcesStorage),
      new CapabilitiesHandler
    )(statsReceiver)
  }

}
