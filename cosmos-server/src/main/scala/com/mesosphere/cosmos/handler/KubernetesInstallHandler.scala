package com.mesosphere.cosmos.handler

import java.io.{StringReader, StringWriter}
import java.util.Base64
import scala.collection.JavaConverters._
import cats.data.Xor
import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.parse.parse
import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject}
import io.finch.DecodeRequest
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.jsonschema.JsonSchemaValidation
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.thirdparty.kubernetes._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.{CirceError, JsonSchemaMismatch, PackageFileNotJson, PackageRunner}
import com.mesosphere.universe.{PackageFiles, Resource}

private[cosmos] final class KubernetesInstallHandler(
  packageCache: PackageCollection,
  packageRunner: PackageRunner[KubernetesService]
)(implicit
  bodyDecoder: DecodeRequest[InstallKubernetesRequest],
  encoder: Encoder[InstallKubernetesResponse]
) extends EndpointHandler[InstallKubernetesRequest, InstallKubernetesResponse] {

  val accepts = MediaTypes.InstallKubernetesRequest
  val produces = MediaTypes.InstallKubernetesResponse

  import KubernetesInstallHandler._

  override def apply(request: InstallKubernetesRequest)(implicit session: RequestSession): Future[InstallKubernetesResponse] = {
    packageCache
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .flatMap { packageFiles =>
        val packageConfig = preparePackageConfig(request.options, packageFiles)
        packageRunner
          .launch(packageConfig, request.namespace)
          .map { runnerResponse =>
            val packageName = packageFiles.packageJson.name
            val packageVersion = packageFiles.packageJson.version

            InstallKubernetesResponse(packageName, packageVersion, "Service", "v1")
          }
      }
  }
}

private[cosmos] object KubernetesInstallHandler {

  import com.mesosphere.cosmos.circe.Encoders._  //TODO: Not crazy about this being here
  private[this] val MustacheFactory = new DefaultMustacheFactory()

  private[cosmos] def preparePackageConfig(
    options: Option[JsonObject],
    packageFiles: PackageFiles
  ): Json = {
    val mergedOptions = mergeOptions(packageFiles, options)
    renderMustacheTemplate(packageFiles, mergedOptions)
   }

  private[this] def validConfig(options: JsonObject, config: JsonObject): JsonObject = {
    val validationErrors = JsonSchemaValidation.matchesSchema(options, config)
    if (validationErrors.nonEmpty) {
      throw JsonSchemaMismatch(validationErrors)
    }
    options
  }

  private[this] def mergeOptions(
    packageFiles: PackageFiles,
    options: Option[JsonObject]
  ): Json = {
    val defaults = extractDefaultsFromConfig(packageFiles.configJson)
    val merged: JsonObject = (packageFiles.configJson, options) match {
      case (None, None) => JsonObject.empty
      case (Some(config), None) => validConfig(defaults, config)
      case (None, Some(_)) =>
        val error = Map("message" -> "No schema available to validate the provided options").asJson
        throw JsonSchemaMismatch(List(error))
      case (Some(config), Some(opts)) =>
        val m = merge(defaults, opts)
        validConfig(m, config)
    }

    val resource = extractAssetsAsJson(packageFiles.resourceJson)
    val complete = merged + ("resource", Json.fromJsonObject(resource))
    Json.fromJsonObject(complete)
  }

  private[this] def renderMustacheTemplate(
    packageFiles: PackageFiles,
    mergedOptions: Json
  ): Json = {
    val strReader = new StringReader(packageFiles.kubernetesJsonMustache)
    val mustache = MustacheFactory.compile(strReader, "kubernetes.json.mustache")
    val params = jsonToJava(mergedOptions)

    val output = new StringWriter()
    mustache.execute(output, params)
    parse(output.toString) match {
      case Xor.Left(err) => throw PackageFileNotJson("kubernetes.json", err.message)
      case Xor.Right(rendered) => rendered
    }
  }

  private[this] def extractAssetsAsJson(resource: Option[Resource]): JsonObject = {
    val assets = resource.map(_.assets) match {
      case Some(a) => a.asJson
      case _ => Json.obj()
    }

    JsonObject.singleton("assets", assets)
  }

  private[this] def extractDefaultsFromConfig(configJson: Option[JsonObject]): JsonObject = {
    configJson
      .flatMap { json =>
        val topProperties =
          json("properties")
            .getOrElse(Json.empty)

        filterDefaults(topProperties)
          .asObject
      }
      .getOrElse(JsonObject.empty)
  }

  private[this] def filterDefaults(properties: Json): Json = {
    val defaults = properties
      .asObject
      .getOrElse(JsonObject.empty)
      .toMap
      .flatMap { case (propertyName, propertyJson) =>
        propertyJson
          .asObject
          .flatMap { propertyObject =>
            propertyObject("default").orElse {
              propertyObject("properties").map(filterDefaults)
            }
          }
          .map(propertyName -> _)
      }

    Json.fromJsonObject(JsonObject.fromMap(defaults))
  }

  private[this] def jsonToJava(json: Json): Any = {
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = n => n.toInt.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(jsonToJava).asJava,
      jsonObject = _.toMap.mapValues(jsonToJava).asJava
    )
  }

  private[cosmos] def merge(target: JsonObject, fragment: JsonObject): JsonObject = {
    fragment.toList.foldLeft(target) { (updatedTarget, fragmentEntry) =>
      val (fragmentKey, fragmentValue) = fragmentEntry
      val targetValueOpt = updatedTarget(fragmentKey)

      val mergedValue = (targetValueOpt.flatMap(_.asObject), fragmentValue.asObject) match {
        case (Some(targetObject), Some(fragmentObject)) =>
          Json.fromJsonObject(merge(targetObject, fragmentObject))
        case _ => fragmentValue
      }

      updatedTarget + (fragmentKey, mergedValue)
    }
  }

}
