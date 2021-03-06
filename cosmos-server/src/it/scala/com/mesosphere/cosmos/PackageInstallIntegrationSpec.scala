package com.mesosphere.cosmos

import java.util.{Base64, UUID}
import cats.data.Xor
import cats.data.Xor.Right
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model._
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

    "reports an error if the requested package is not in the cache" in {
      forAll (PackageTable) { (packageName, _) =>
        val errorResponse = ErrorResponse(
          `type` = "PackageNotFound",
          message = s"Package [$packageName] not found",
          data = Some(JsonObject.singleton("packageName", packageName.asJson))
        )

        installPackageAndAssert(
          InstallRequest(packageName),
          expectedResult = Failure(Status.BadRequest, errorResponse),
          preInstallState = Anything,
          postInstallState = Unchanged
        )
      }
    }

    "don't install if specified version is not found" in {
      forAll (PackageDummyVersionsTable) { (packageName, packageVersion) =>
        val errorMessage = s"Version [$packageVersion] of package [$packageName] not found"
        val errorResponse = ErrorResponse(
          `type` = "VersionNotFound",
          message = errorMessage,
          data = Some(JsonObject.fromMap(Map(
            "packageName" -> packageName.asJson,
            "packageVersion" -> packageVersion.toString.asJson
          )))
        )

        // TODO This currently relies on test execution order to be correct
        // Update it to explicitly install a package twice
        installPackageAndAssert(
          InstallRequest(packageName, packageVersion = Some(packageVersion)),
          expectedResult = Failure(Status.BadRequest, errorResponse),
          preInstallState = NotInstalled,
          postInstallState = Unchanged
        )
      }
    }

    "can successfully install packages from Universe" in {
      forAll (UniversePackagesTable) { (expectedResponse, forceVersion, uriSet, labelsOpt) =>
        val versionOption = if (forceVersion) Some(expectedResponse.packageVersion) else None

        installPackageAndAssert(
          InstallRequest(expectedResponse.packageName, packageVersion = versionOption),
          expectedResult = Success(expectedResponse),
          preInstallState = NotInstalled,
          postInstallState = Installed
        )
        // TODO Confirm that the correct config was sent to Marathon - see issue #38
        val packageInfo = Await.result(getMarathonApp(expectedResponse.appId))
        assertResult(uriSet)(packageInfo.uris.toSet)
        labelsOpt.foreach(labels => assertResult(labels)(StandardLabels(packageInfo.labels)))

        // Assert that installing twice gives us a package already installed error
        installPackageAndAssert(
          InstallRequest(expectedResponse.packageName, packageVersion = versionOption),
          expectedResult = Failure(
            Status.Conflict,
            ErrorResponse(
              "PackageAlreadyInstalled",
              "Package is already installed",
              Some(JsonObject.empty)
            ),
            Some(expectedResponse.appId)
          ),
          preInstallState = AlreadyInstalled,
          postInstallState = Unchanged
        )
      }
    }

    "supports custom app IDs" in {
      val expectedResponse = InstallResponse("cassandra", PackageDetailsVersion("0.2.0-2"), AppId("custom-app-id"))

      installPackageAndAssert(
        InstallRequest(expectedResponse.packageName, appId = Some(expectedResponse.appId)),
        expectedResult = Success(expectedResponse),
        preInstallState = NotInstalled,
        postInstallState = Installed
      )
    }

    "validates merged config template options JSON schema" in {
      val Some(badOptions) = Map("chronos" -> Map("zk-hosts" -> false)).asJson.asObject

      val schemaError = JsonObject.fromIndexedSeq {
        Vector(
          "level" -> "error".asJson,
          "schema" -> Map(
            "loadingURI" -> "#",
            "pointer" -> "/properties/chronos/properties/zk-hosts"
          ).asJson,
          "instance" -> Map("pointer" -> "/chronos/zk-hosts").asJson,
          "domain" -> "validation".asJson,
          "keyword" -> "type".asJson,
          "message" -> "instance type (boolean) does not match any allowed primitive type (allowed: [\"string\"])".asJson,
          "found" -> "boolean".asJson,
          "expected" -> List("string").asJson
        )
      }.asJson

      val errorData = JsonObject.singleton("errors", List(schemaError).asJson)
      val errorResponse =
        ErrorResponse("JsonSchemaMismatch", "Options JSON failed validation", Some(errorData))

      val appId = AppId("chronos-bad-json")

      installPackageAndAssert(
        InstallRequest("chronos", options = Some(badOptions), appId = Some(appId)),
        expectedResult = Failure(Status.BadRequest, errorResponse),
        preInstallState = Anything,
        postInstallState = Unchanged
      )
    }

  }

  override protected def beforeAll(): Unit = { /*no-op*/ }

  override protected def afterAll(): Unit = {
    // TODO: This should actually happen between each test, but for now tests depend on eachother :(
    val deletes: Future[Seq[Unit]] = Future.collect(Seq(
      adminRouter.deleteApp(AppId("/helloworld"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/cassandra/dcos"), force = true) map { resp => assert(resp.getStatusCode() === 200) },
      adminRouter.deleteApp(AppId("/custom-app-id"), force = true) map { resp => assert(resp.getStatusCode() === 200) },

      // Make sure this is cleaned up if its test failed
      adminRouter.deleteApp(AppId("/chronos-bad-json"), force = true) map { _ => () }
    ))
    Await.result(deletes.flatMap { x => Future.Unit })
  }

  private[cosmos] def installPackageAndAssert(
    installRequest: InstallRequest,
    expectedResult: ExpectedResult,
    preInstallState: PreInstallState,
    postInstallState: PostInstallState
  ): Unit = {
    val appId = expectedResult.appId.getOrElse(AppId(installRequest.packageName))

    val packageWasInstalled = isAppInstalled(appId)
    preInstallState match {
      case AlreadyInstalled => assertResult(true)(packageWasInstalled)
      case NotInstalled => assertResult(false)(packageWasInstalled)
      case Anything => // Don't care
    }

    val response = installPackage(installRequest)

    assertResult(expectedResult.status)(response.status)
    expectedResult match {
      case Success(expectedBody) =>
        val Xor.Right(actualBody) = decode[InstallResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
      case Failure(_, expectedBody, _) =>
        val Xor.Right(actualBody) = decode[ErrorResponse](response.contentString)
        assertResult(expectedBody)(actualBody)
    }

    val expectedInstalled = postInstallState match {
      case Installed => true
      case Unchanged => packageWasInstalled
    }
    val actuallyInstalled = isAppInstalled(appId)
    assertResult(expectedInstalled)(actuallyInstalled)
  }

  private[this] def isAppInstalled(appId: AppId): Boolean = {
    Await.result {
      adminRouter.getAppOption(appId)
        .map(_.isDefined)
    }
  }

  private[this] def installPackage(
    installRequest: InstallRequest
  ): Response = {
    val request = CosmosClient.requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8(installRequest.asJson.noSpaces))
    CosmosClient(request)
  }

}

private object PackageInstallIntegrationSpec extends Matchers with TableDrivenPropertyChecks {

  private val PackageTableRows: Seq[(String, PackageFiles)] = Seq(
    packageTableRow("helloworld2", 1, 512.0, 2),
    packageTableRow("helloworld3", 0.75, 256.0, 3)
  )

  private lazy val PackageTable = Table(
    ("package name", "package files"),
    PackageTableRows: _*
  )

  private val PackageDummyVersionsTable = Table(
    ("package name", "version"),
    ("helloworld", PackageDetailsVersion("a.b.c")),
    ("cassandra", PackageDetailsVersion("foobar"))
  )

  private val HelloWorldLabels = StandardLabels(
    Map(
      ("DCOS_PACKAGE_METADATA", "eyJ3ZWJzaXRlIjoiaHR0cHM6Ly9naXRodWIuY29tL21lc29zcGhlcmUvZGNvcy1" +
        "oZWxsb3dvcmxkIiwibmFtZSI6ImhlbGxvd29ybGQiLCJwb3N0SW5zdGFsbE5vdGVzIjoiQSBzYW1wbGUgcG9zdC" +
        "1pbnN0YWxsYXRpb24gbWVzc2FnZSIsImRlc2NyaXB0aW9uIjoiRXhhbXBsZSBEQ09TIGFwcGxpY2F0aW9uIHBhY" +
        "2thZ2UiLCJwYWNrYWdpbmdWZXJzaW9uIjoiMi4wIiwidGFncyI6WyJtZXNvc3BoZXJlIiwiZXhhbXBsZSIsInN1" +
        "YmNvbW1hbmQiXSwibWFpbnRhaW5lciI6InN1cHBvcnRAbWVzb3NwaGVyZS5pbyIsInZlcnNpb24iOiIwLjEuMCI" +
        "sInByZUluc3RhbGxOb3RlcyI6IkEgc2FtcGxlIHByZS1pbnN0YWxsYXRpb24gbWVzc2FnZSJ9"),
      ("DCOS_PACKAGE_COMMAND", "eyJwaXAiOlsiZGNvczwxLjAiLCJnaXQraHR0cHM6Ly9naXRodWIuY29tL21lc29z" +
        "cGhlcmUvZGNvcy1oZWxsb3dvcmxkLmdpdCNkY29zLWhlbGxvd29ybGQ9MC4xLjAiXX0="),
      "DCOS_PACKAGE_REGISTRY_VERSION" -> "2.0",
      "DCOS_PACKAGE_NAME" -> "helloworld",
      "DCOS_PACKAGE_VERSION" -> "0.1.0",
      "DCOS_PACKAGE_SOURCE" -> DefaultRepositories().getOrThrow(1).uri.toString,
      "DCOS_PACKAGE_RELEASE" -> "0"
    )
  )

  private val CassandraUris = Set(
    "https://downloads.mesosphere.com/cassandra-mesos/artifacts/0.2.0-1/cassandra-mesos-0.2.0-1.tar.gz",
    "https://downloads.mesosphere.com/java/jre-7u76-linux-x64.tar.gz"
  )

  private val UniversePackagesTable = Table(
    ("expected response", "force version", "URI list", "Labels"),
    (InstallResponse("helloworld", PackageDetailsVersion("0.1.0"), AppId("helloworld")), false, Set.empty[String], Some(HelloWorldLabels)),
    (InstallResponse("cassandra", PackageDetailsVersion("0.2.0-1"), AppId("cassandra/dcos")), true, CassandraUris, None)
  )

  private def getMarathonApp(appId: AppId)(implicit session: RequestSession): Future[MarathonApp] = {
    CosmosIntegrationTestClient.adminRouter.getApp(appId)
      .map(_.app)
  }

  private def packageTableRow(
    name: String, cpus: Double, mem: Double, pythonVersion: Int
  ): (String, PackageFiles) = {
    val cmd =
      if (pythonVersion <= 2) "python2 -m SimpleHTTPServer 8082" else "python3 -m http.server 8083"

    val packageDefinition = PackageDetails(
      packagingVersion = PackagingVersion("2.0"),
      name = name,
      version = PackageDetailsVersion("0.1.0"),
      maintainer = "Mesosphere",
      description = "Test framework",
      tags = Nil,
      scm = None,
      website = None,
      framework = None,
      preInstallNotes = None,
      postInstallNotes = None,
      postUninstallNotes = None,
      licenses = None
    )

    val marathonJson = MarathonApp(
      id = AppId(name),
      cpus = cpus,
      mem = mem,
      instances = 1,
      cmd = Some(cmd),
      container = Some(MarathonAppContainer(
        `type` = "DOCKER",
        docker = Some(MarathonAppContainerDocker(
          image = s"python:$pythonVersion",
          network = Some("HOST")
        ))
      )),
      labels = Map("test-id" -> UUID.randomUUID().toString),
      uris = List.empty
    )

    val packageFiles = PackageFiles(
      revision = "0",
      sourceUri = Uri.parse("in/memory/source"),
      packageJson = packageDefinition,
      marathonJsonMustache = marathonJson.asJson.noSpaces
    )

    (name, packageFiles)
  }
}

private sealed abstract class ExpectedResult(val status: Status, val appId: Option[AppId])

private case class Success(body: InstallResponse)
  extends ExpectedResult(Status.Ok, Some(body.appId))

private case class Failure(
  override val status: Status,
  body: ErrorResponse,
  override val appId: Option[AppId] = None
) extends ExpectedResult(status, appId)

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
