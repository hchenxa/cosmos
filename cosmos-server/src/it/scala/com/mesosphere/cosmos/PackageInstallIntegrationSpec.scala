package com.mesosphere.cosmos

import java.util.{Base64, UUID}
import cats.data.Xor
import cats.data.Xor.Right
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.thirdparty.kubernetes._
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonApp, MarathonAppContainer, MarathonAppContainerDocker}
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.universe.{PackageDetails, PackageDetailsVersion, PackageFiles, PackagingVersion}
import com.netaporter.uri.Uri
import com.twitter.finagle.http._
import com.twitter.io.{Buf, Charsets}
import com.twitter.util.{Await, Future}
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

final class PackageInstallIntegrationSpec extends FreeSpec with BeforeAndAfterAll {

  import PackageInstallIntegrationSpec._
  import CosmosIntegrationTestClient._

  "The package install endpoint" - {

    "can successfully install packages from Universe" in {
      forAll (UniversePackagesTable) { (expectedResponse, uriSet) =>
        val versionOption = Some(expectedResponse.packageVersion)

        installPackageAndAssert(
          InstallKubernetesRequest(expectedResponse.packageName, packageVersion = versionOption),
          expectedResult = Success(expectedResponse),
          preInstallState = NotInstalled,
          postInstallState = Installed
        )

        // Assert that installing twice gives us a package already installed error
//        installPackageAndAssert(
//          InstallKubernetesRequest(expectedResponse.packageName, packageVersion = versionOption),
//          expectedResult = Failure(
//            Status.Conflict,
//            ErrorResponse(
//              "PackageAlreadyInstalled",
//              "Package is already installed",
//              Some(JsonObject.empty)
//            ),
//            None,
//            None
//          ),
//          preInstallState = AlreadyInstalled,
//          postInstallState = Unchanged
//        )
      }
    }

  }

  override protected def beforeAll(): Unit = { /*no-op*/ }

  override protected def afterAll(): Unit = {
    // TODO: This should actually happen between each test, but for now tests depend on eachother :(
//    val deletes: Future[Seq[Unit]] = Future.collect(Seq(
//      adminRouter.deleteService("webServer", "default") map { resp => assert(resp.getStatusCode() === 200) },
//      adminRouter.deleteRC("webServer", "default") map { resp => assert(resp.getStatusCode() === 200) }
//    ))
//    Await.result(deletes.flatMap { x => Future.Unit })
  }

  private[cosmos] def installPackageAndAssert(
    installRequest: InstallKubernetesRequest,
    expectedResult: ExpectedResult,
    preInstallState: PreInstallState,
    postInstallState: PostInstallState
  ): Unit = {
    val svc = expectedResult.svc.getOrElse("nginx")
    val namespace = expectedResult.namespace.getOrElse("default")

    val response = installPackage(installRequest)

    assertResult(expectedResult.status)(response.status)
    expectedResult match {
      case Success(expectedBody) =>
        val Xor.Right(actualBody) = decode[InstallKubernetesResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
      case Failure(_, expectedBody, _, _) =>
        val Xor.Right(actualBody) = decode[ErrorResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
    }

    val expectedInstalled = postInstallState match {
      case Installed => true
      case Unchanged => false
    }
    val service = serviceDeployed(svc, namespace)
    assertResult("Service")(service.kind)
  }

//  private[this] def isAppInstalled(appId: AppId): Boolean = {
//    Await.result {
//      adminRouter.getAppOption(appId)
//        .map(_.isDefined)
//    }
//  }
  
  private[this] def serviceDeployed(svc: String, namespace: String): KubernetesService = {
    Await.result {
      adminRouter.getService(svc, namespace)
        .map(_.service)
    }
  }

  private[this] def installPackage(
    installRequest: InstallKubernetesRequest
  ): Response = {
    val request = CosmosClient.requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallKubernetesRequest.show)
      .addHeader("Accept", MediaTypes.InstallKubernetesResponse.show)
      .buildPost(Buf.Utf8(installRequest.asJson.noSpaces))
    CosmosClient(request)
  }

}

private object PackageInstallIntegrationSpec extends Matchers with TableDrivenPropertyChecks {

  private val PackageTableRows: Seq[(String, PackageFiles)] = Seq(
    packageTableRow("webServer")
  )

  private lazy val PackageTable = Table(
    ("package name", "package files"),
    PackageTableRows: _*
  )

  private val PackageDummyVersionsTable = Table(
    ("package name", "version"),
    ("nginx", PackageDetailsVersion("a.b.c"))
  )

//  private val HelloWorldLabels = StandardLabels(
//    Map(
//      ("DCOS_PACKAGE_METADATA", "eyJ3ZWJzaXRlIjoiaHR0cHM6Ly9naXRodWIuY29tL21lc29zcGhlcmUvZGNvcy1" +
//        "oZWxsb3dvcmxkIiwibmFtZSI6ImhlbGxvd29ybGQiLCJwb3N0SW5zdGFsbE5vdGVzIjoiQSBzYW1wbGUgcG9zdC" +
//        "1pbnN0YWxsYXRpb24gbWVzc2FnZSIsImRlc2NyaXB0aW9uIjoiRXhhbXBsZSBEQ09TIGFwcGxpY2F0aW9uIHBhY" +
//        "2thZ2UiLCJwYWNrYWdpbmdWZXJzaW9uIjoiMi4wIiwidGFncyI6WyJtZXNvc3BoZXJlIiwiZXhhbXBsZSIsInN1" +
//        "YmNvbW1hbmQiXSwibWFpbnRhaW5lciI6InN1cHBvcnRAbWVzb3NwaGVyZS5pbyIsInZlcnNpb24iOiIwLjEuMCI" +
//        "sInByZUluc3RhbGxOb3RlcyI6IkEgc2FtcGxlIHByZS1pbnN0YWxsYXRpb24gbWVzc2FnZSJ9"),
//      ("DCOS_PACKAGE_COMMAND", "eyJwaXAiOlsiZGNvczwxLjAiLCJnaXQraHR0cHM6Ly9naXRodWIuY29tL21lc29z" +
//        "cGhlcmUvZGNvcy1oZWxsb3dvcmxkLmdpdCNkY29zLWhlbGxvd29ybGQ9MC4xLjAiXX0="),
//      "DCOS_PACKAGE_REGISTRY_VERSION" -> "2.0",
//      "DCOS_PACKAGE_NAME" -> "helloworld",
//      "DCOS_PACKAGE_VERSION" -> "0.1.0",
//      "DCOS_PACKAGE_SOURCE" -> DefaultRepositories().getOrThrow(1).uri.toString,
//      "DCOS_PACKAGE_RELEASE" -> "0"
//    )
//  )

//  private val CassandraUris = Set(
//    "https://downloads.mesosphere.com/cassandra-mesos/artifacts/0.2.0-1/cassandra-mesos-0.2.0-1.tar.gz",
//    "https://downloads.mesosphere.com/java/jre-7u76-linux-x64.tar.gz"
//  )

  private val UniversePackagesTable = Table(
    ("expected response", "URI list"),
    (InstallKubernetesResponse("nginx", PackageDetailsVersion("0.1.0"), "Service", "v1"), Set.empty[String])
  )

//  private def getMarathonApp(appId: AppId)(implicit session: RequestSession): Future[MarathonApp] = {
//    CosmosIntegrationTestClient.adminRouter.getApp(appId)
//      .map(_.app)
//  }
  
  private def getKubernetesService(svc: String, namespace: String)(implicit session: RequestSession): Future[KubernetesService] = {
    CosmosIntegrationTestClient.adminRouter.getService(svc, namespace)
      .map(_.service)
  }

  private def packageTableRow(name: String): (String, PackageFiles) = {
//    val cmd =
//      if (pythonVersion <= 2) "python2 -m SimpleHTTPServer 8082" else "python3 -m http.server 8083"

    val packageDefinition = PackageDetails(
      packagingVersion = PackagingVersion("2.0"),
      name = name,
      version = PackageDetailsVersion("0.1.0"),
      maintainer = "IBM",
      description = "Example Kubernetes service",
      tags = Nil,
      scm = None,
      website = None,
      framework = None,
      preInstallNotes = None,
      postInstallNotes = None,
      postUninstallNotes = None,
      licenses = None
    )

//    val marathonJson = MarathonApp(
//      id = AppId(name),
//      cpus = cpus,
//      mem = mem,
//      instances = 1,
//      cmd = Some(cmd),
//      container = Some(MarathonAppContainer(
//        `type` = "DOCKER",
//        docker = Some(MarathonAppContainerDocker(
//          image = s"python:$pythonVersion",
//          network = Some("HOST")
//        ))
//      )),
//      labels = Map("test-id" -> UUID.randomUUID().toString),
//      uris = List.empty
//    )
    
    val kubeService =
      """
        |{
        |  "id": "/nginx",
        |  "apps": [
        |    {
        |        "apiVersion": "v1",
        |        "kind": "Service",
        |        "metadata": {
        |            "name": "{{nginx.name}}",
        |            "labels": {
        |                "app": "nginx"
        |            }
        |        },
        |        "spec": {
        |            "ports": [
        |                {
        |                    "protocol": "TCP",
        |                    "port": 80,
        |                    "targetPort": 80
        |                }
        |            ],
        |            "selector": {
        |                "app": "nginx"
        |            }
        |        }
        |    },
        |    {
        |        "apiVersion": "v1",
        |        "kind": "ReplicationController",
        |        "metadata": {
        |            "name": "{{nginx.name}}",
        |            "labels": {
        |                "app": "nginx"
        |            }
        |        },
        |        "spec": {
        |            "replicas": 1,
        |            "selector": {
        |                "app": "nginx"
        |            },
        |            "template": {
        |                "metadata": {
        |                    "name": "{{nginx.name}}",
        |                    "labels": {"app": "nginx"}
        |                },
        |                "spec": {
        |                    "containers": [
        |                        {
        |                            "name": "{{nginx.name}}",
        |                            "image": "{{resource.assets.container.docker.nginx-docker}}",
        |                            "resources": {
        |                              "requests": {
        |                                "cpu": "100m"
        |                              }
        |                            },
        |                            "imagePullPolicy": "Always",
        |                            "securityContext": {
        |                                "privileged": false
        |                            }
        |                        }
        |                    ],
        |                    "restartPolicy": "Always",
        |                    "terminationGracePeriodSeconds": 30,
        |                    "dnsPolicy": "ClusterFirst",
        |                    "securityContext": {}
        |                }
        |            }
        |        }
        |    }
        |   ]
        |}
      """.stripMargin

    val packageFiles = PackageFiles(
      revision = "0",
      sourceUri = Uri.parse("in/memory/source"),
      packageJson = packageDefinition,
      marathonJsonMustache = null,
      kubernetesJsonMustache = kubeService
    )

    (name, packageFiles)
  }
}

private sealed abstract class ExpectedResult(val status: Status, val svc: Option[String], val namespace: Option[String])

private case class Success(body: InstallKubernetesResponse)
  extends ExpectedResult(Status.Ok, None, None)

private case class Failure(
  override val status: Status,
  body: ErrorResponse,
  override val svc: Option[String] = None,
  override val namespace: Option[String] = None  
) extends ExpectedResult(status, svc, namespace)

private sealed trait PreInstallState
private case object AlreadyInstalled extends PreInstallState
private case object NotInstalled extends PreInstallState
private case object Anything extends PreInstallState

private sealed trait PostInstallState
private case object Installed extends PostInstallState
private case object Unchanged extends PostInstallState

case class StandardLabels(
  packageMetadata: Json,
  packageCommand: Json,
  packageRegistryVersion: String,
  packageName: String,
  packageVersion: String,
  packageSource: String,
  packageRelease: String
)

object StandardLabels {

  def apply(labels: Map[String, String]): StandardLabels = {
    StandardLabels(
      packageMetadata = decodeAndParse(labels("DCOS_PACKAGE_METADATA")),
      packageCommand = decodeAndParse(labels("DCOS_PACKAGE_COMMAND")),
      packageRegistryVersion = labels("DCOS_PACKAGE_REGISTRY_VERSION"),
      packageName = labels("DCOS_PACKAGE_NAME"),
      packageVersion = labels("DCOS_PACKAGE_VERSION"),
      packageSource = labels("DCOS_PACKAGE_SOURCE"),
      packageRelease = labels("DCOS_PACKAGE_RELEASE")
    )
  }

  private[this] def decodeAndParse(encoded: String): Json = {
    val decoded = new String(Base64.getDecoder.decode(encoded), Charsets.Utf8)
    val Right(parsed) = parse(decoded)
    parsed
  }

}
