package com.mesosphere.cosmos.circe

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.model.{AppId, PackageRepository}
import com.mesosphere.universe.Images
import com.netaporter.uri.Uri
import io.circe._
import io.circe.generic.semiauto
import io.circe.parse._
import io.circe.syntax._
import org.scalatest.FreeSpec

final class EncodersDecodersSpec extends FreeSpec {

  import EncodersDecodersSpec._

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
      assertResult(images)(unsafeDecodeJson[Images](json))
    }
    "round-trip" in {
      assertResult(images)(unsafeDecodeJson[Images](images.asJson))
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

  "ExampleVersionedAdt is encoded without class labels" - {
    "because the version can be determined from the Content-Type response header" - {

      "encoding of Version0" in {
        assertResult(Json.obj()) {
          (Version0: ExampleVersionedAdt).asJson
        }
      }

      "encoding of Version1" in {
        assertResult(Json.obj("a" -> "foo".asJson)) {
          (Version1("foo"): ExampleVersionedAdt).asJson
        }
      }

      "encoding of Version2" in {
        assertResult(Json.obj("a" -> "bar".asJson, "b" -> 42.asJson)) {
          (Version2("bar", 42): ExampleVersionedAdt).asJson
        }
      }

      "encoding of Version3" in {
        assertResult(Json.obj("b" -> 123.asJson, "c" -> 2.18282.asJson)) {
          (Version3(123, 2.18282): ExampleVersionedAdt).asJson
        }
      }

    }
  }

}

object EncodersDecodersSpec {

  implicit val encodeExampleVersionedAdt: Encoder[ExampleVersionedAdt] = {
    removeClassLabelsFromEncoding(semiauto.deriveFor[ExampleVersionedAdt].encoder)
  }

  private def unsafeDecodeJson[A: Decoder](json: Json)(implicit decoder: Decoder[A]): A = {
    decoder.decodeJson(json).getOrElse(throw new AssertionError("Unable to decode"))
  }

}
