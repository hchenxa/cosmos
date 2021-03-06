package com.mesosphere.cosmos.circe

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.model.thirdparty.adminrouter.DcosVersion
import com.mesosphere.cosmos.model.{AppId, PackageRepository}
import com.mesosphere.universe.Images
import com.mesosphere.universe.v3.DcosReleaseVersion
import com.mesosphere.universe.v3.DcosReleaseVersion.{Suffix, Version}
import com.netaporter.uri.Uri
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, Json, JsonObject, ParsingFailure}
import org.scalatest.FreeSpec

class EncodersDecodersSpec extends FreeSpec {
  import Decoders._
  import Encoders._

  "Images" - {
    val json = Json.obj(
      "icon-small" -> "http://some.place/icon-small.png".asJson,
      "icon-medium" -> "http://some.place/icon-medium.png".asJson,
      "icon-large" -> "http://some.place/icon-large.png".asJson,
      "screenshots" -> List(
        "http://some.place/screenshot-1.png",
        "http://some.place/screenshot-2.png"
      ).asJson
    )
    val images = Images(
      iconSmall = "http://some.place/icon-small.png",
      iconMedium = "http://some.place/icon-medium.png",
      iconLarge = "http://some.place/icon-large.png",
      screenshots = Some(List(
        "http://some.place/screenshot-1.png",
        "http://some.place/screenshot-2.png"
      ))
    )
    "encoder" in {
      assertResult(json)(images.asJson)
    }
    "decode" in {
      assertResult(images)(decodeJson[Images](json))
    }
    "round-trip" in {
      assertResult(images)(decodeJson[Images](images.asJson))
    }
  }

  "AppId" - {
    val relative: String = "cassandra/dcos"
    val absolute: String = s"/$relative"
    "encode" in {
      assertResult(Json.string(absolute))(AppId(relative).asJson)
    }
    "decode" in {
      assertResult(Xor.Right(AppId(absolute)))(decode[AppId](relative.asJson.noSpaces))
    }
  }

  "CosmosError" - {
    "RepositoryUriSyntax" in {
      assertRoundTrip("RepositoryUriSyntax", RepositoryUriSyntax.apply)
    }


    "RepositoryUriConnection" in {
      assertRoundTrip("RepositoryUriConnection", RepositoryUriConnection.apply)
    }

    def assertRoundTrip(
      errorType: String,
      errorConstructor: (PackageRepository, Throwable) => Exception
    ): Unit = {
      val repo = PackageRepository("repo", Uri.parse("http://example.com"))
      val cause = "original failure message"
      val error = errorConstructor(repo, new Throwable(cause))

      val Xor.Right(roundTripError) = error.asJson.as[ErrorResponse]
      assertResult(errorType)(roundTripError.`type`)
      assertResult(Some(JsonObject.singleton("cause", cause.asJson)))(roundTripError.data)
    }
  }

  "Throwable fields are dropped from encoded objects" - {
    val throwable = new RuntimeException("BOOM!")

    "PackageFileMissing" in {
      assertThrowableDropped(PackageFileMissing(packageName = "kafka", cause = throwable), "cause")
    }

    "CirceError" in {
      assertThrowableDropped(CirceError(cerr = ParsingFailure("failed", throwable)), "cerr")
    }

    "ServiceUnavailable" in {
      val error = ServiceUnavailable(serviceName = "mesos", causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    "IncompleteUninstall" in {
      val error = IncompleteUninstall(packageName = "spark", causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    "ConcurrentAccess" in {
      assertThrowableDropped(ConcurrentAccess(causedBy = throwable), "causedBy")
    }

    "RepositoryUriSyntax" in {
      val repo = PackageRepository("Universe", Uri.parse("universe/repo"))
      val error = RepositoryUriSyntax(repository = repo, causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    "RepositoryUriConnection" in {
      val repo = PackageRepository("Universe", Uri.parse("universe/repo"))
      val error = RepositoryUriConnection(repository = repo, causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    def assertThrowableDropped[A <: CosmosError with Product](
      error: A,
      throwableFieldNames: String*
    ): Unit = {
      val encodedFields = error.getData.getOrElse(JsonObject.empty)
      throwableFieldNames.foreach(name => assert(!encodedFields.contains(name), name))
      assertResult(error.productArity - throwableFieldNames.size)(encodedFields.size)
    }
  }

  "DcosVersion" - {
    "decode" in {
      val json = Json.obj(
        "version" -> "1.7.1".asJson,
        "dcos-image-commit" -> "0defde84e7a71ebeb5dfeca0936c75671963df48".asJson,
        "bootstrap-id" -> "f12bff891be7108962c7c98e530e1f2cd8d4e56b".asJson
      )

      val expected = DcosVersion(
        DcosReleaseVersion(Version(1), List(Version(7), Version(1))),
        "0defde84e7a71ebeb5dfeca0936c75671963df48",
        "f12bff891be7108962c7c98e530e1f2cd8d4e56b"
      )

      assertResult(expected)(decodeJson[DcosVersion](json))
    }
  }

  "DcosReleaseVersion" - {
    val str = "2.10.153-beta"
    val obj = DcosReleaseVersion(Version(2), List(Version(10), Version(153)), Some(Suffix("beta")))
    "decode"  in {
      assertResult(obj)(decodeJson[DcosReleaseVersion](str.asJson))
    }
    "encode" in {
      assertResult(Json.string(str))(obj.asJson)
    }
  }

  private[this] def decodeJson[A: Decoder](json: Json)(implicit decoder: Decoder[A]): A = {
    decoder.decodeJson(json).getOrElse(throw new AssertionError("Unable to decode"))
  }

}
