package com.mesosphere.cosmos.handler

import cats.Eval
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.circe.Encoders
import com.mesosphere.cosmos.http.{MediaType, MediaTypes, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Try}
import io.circe.generic.semiauto
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.finch.{/, Endpoint, Input, Output, post}

final class VersionedResponsesSpec extends UnitSpec {

  import VersionedResponsesSpec._

  "The Accept header determines the version of the response to send" - {

    "Foo version" in {
      val input = buildInput(Foo.MediaType, "42")
      val result = FoobarEndpoint(input)
      val jsonBody = extractBody(result)
      assertResult(Json.obj("whole" -> 42.asJson))(jsonBody)
    }

    "Bar version" in {
      val input = buildInput(Bar.MediaType, "3.14159")
      val result = FoobarEndpoint(input)
      val jsonBody = extractBody(result)
      assertResult(Json.obj("decimal" -> 3.14159.asJson))(jsonBody)
    }

  }

}

object VersionedResponsesSpec {

  final case class FoobarResponse(foo: Int, bar: Double)

  sealed trait VersionedFoobarResponse
  final case class Foo(whole: Int) extends VersionedFoobarResponse
  final case class Bar(decimal: Double) extends VersionedFoobarResponse

  object Foo {
    val MediaType: MediaType = versionedJson(1)
    def fromGenericResponse(foobar: FoobarResponse): Foo = Foo(foobar.foo)
  }

  object Bar {
    val MediaType: MediaType = versionedJson(2)
    def fromGenericResponse(foobar: FoobarResponse): Bar = Bar(foobar.bar)
  }

  def versionedJson(version: Int): MediaType = {
    MediaTypes.applicationJson.copy(parameters = Some(Map("version" -> s"v$version")))
  }

  implicit val versionedFoobarResponseEncoder: Encoder[VersionedFoobarResponse] = {
    Encoders.removeClassLabelsFromEncoding(semiauto.deriveFor[VersionedFoobarResponse].encoder)
  }

  val foobarCodec: EndpointCodec[String, FoobarResponse, VersionedFoobarResponse] = {
    EndpointCodec(
      requestReader = RequestReaders.standard(
        accepts = MediaTypes.applicationJson,
        produces = Seq(
          Foo.MediaType -> Foo.fromGenericResponse,
          Bar.MediaType -> Bar.fromGenericResponse
        )
      ),
      responseEncoder = versionedFoobarResponseEncoder
    )
  }

  val endpointPath: Seq[String] = Seq("package", "foobar")

  def buildInput(acceptHeader: MediaType, body: String): Input = {
    val request = RequestBuilder()
      .url(s"http://some.host/${endpointPath.mkString("/")}")
      .setHeader("Accept", acceptHeader.show)
      .setHeader("Content-Type", MediaTypes.applicationJson.show)
      .buildPost(Buf.Utf8(body))

    Input(request)
  }

  def extractBody[A](result: Option[(Input, Eval[Future[Output[A]]])]): A = {
    val Some((_, eval)) = result
    Await.result(eval.value).value
  }

  object FoobarHandler extends EndpointHandler()(foobarCodec) {

    override def apply(request: String)(implicit session: RequestSession): Future[FoobarResponse] = {
      val asInt = Try(request.toInt).getOrElse(0)
      val asDouble = Try(request.toDouble).getOrElse(0.0)
      Future(FoobarResponse(asInt, asDouble))
    }

  }

  val FoobarEndpoint: Endpoint[Json] = FoobarHandler.forRoute(post(/))

}
