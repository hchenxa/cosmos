package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, MediaType, MediaTypes, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Await, Return, Try}
import io.circe.Json
import io.finch.RequestReader

final class RequestReadersSpec extends UnitSpec {

  import RequestReadersSpec._

  "The RequestReader built by RequestReaders.noBody should" - {

    behave like baseReader(factory = NoBodyReaderFactory)

  }

  "The RequestReader built by RequestReaders.standard should" - {

    behave like baseReader(factory = StandardReaderFactory)

  }

  def baseReader[Req](factory: RequestReaderFactory[Req]): Unit = {

    "include the Authorization header in the return value if it was included in the request" - {
      "to accurately forward the header's state to other services" in {
        val Return((requestSession, _, _)) = runReader(authorization = Some("53cr37"))
        assertResult(RequestSession(Some(Authorization("53cr37"))))(requestSession)
      }
    }

    "omit the Authorization header from the return value if it was omitted from the request" - {
      "to accurately forward the header's state to other services" in {
        val Return((requestSession, _, _)) = runReader(authorization = None)
        assertResult(RequestSession(None))(requestSession)
      }
    }

    "fail if the Accept header is not a value we support" - {
      "(because we can only encode the response to one of the supported formats)" - {
        "the Accept header is missing" in {
          val result = runReader(accept = None)
          assert(result.isThrow)
        }

        "the Accept header cannot be decoded as a MediaType" in {
          val result = runReader(accept = Some("---not-a-media-type---"))
          assert(result.isThrow)
        }

        "the Accept header is not compatible with a MediaType in `produces`" - {
          "where `produces` is empty" in {
            val result = runReader(produces = Seq.empty)
            assert(result.isThrow)
          }
          "where `produces` contains only incompatible header values" in {
            val result = runReader(accept = Some("text/plain"))
            assert(result.isThrow)
          }
        }
      }
    }

    "if the Accept header has a value we support" - {
      "include the corresponding Content-Type in the return value" - {
        "so that it can be included as a header in the response" in {
          val mediaType = MediaType("application", "json")
          val produces = Seq((mediaType, identity[Unit] _))
          val Return((_, _, responseContentType)) = runReader(produces = produces)
          assertResult(mediaType)(responseContentType)
        }
      }

      "include the first corresponding response formatting function in the return value" - {
        "so that it can be used to correctly format the response" in {
          val produces = Seq[(MediaType, Int => Int)](
            (MediaType("text", "plain"), _ => 0),
            (MediaTypes.applicationJson, _ => 1),
            (MediaTypes.applicationJson, _ => 2)
          )
          val Return((_, responseFormatter, _)) = runReader(produces = produces)
          assertResult(1)(responseFormatter(42))
        }
      }
    }

    def runReader[Res](
      accept: Option[String] = Some(MediaTypes.applicationJson.show),
      authorization: Option[String] = None,
      produces: Seq[(MediaType, Res => Res)] = Seq((MediaTypes.applicationJson, identity[Res] _))
    ): Try[BaseReadValues[Res]] = {
      val request = RequestBuilder()
        .url("http://some.host")
        .setHeader("Accept", accept.toSeq)
        .setHeader("Authorization", authorization.toSeq)
        .setHeader("Content-Type", MediaTypes.applicationJson.show)
        .buildPost(Buf.Utf8(Json.empty.noSpaces))

      val reader = factory(produces)
      Await.result(reader(request).liftToTry).map { context =>
        (context.session, context.responseFormatter, context.responseContentType)
      }
    }

  }

}

object RequestReadersSpec {

  type BaseReadValues[Res] = (RequestSession, Res => Res, MediaType)

  /** This factory trait is needed because the `Req` type is different for each factory function in
    * [[RequestReaders]], but the `Res` type can be different for each test case in `baseReader`.
    *
    * Thus, we cannot just use a factory method, because that would couple `Req` and `Res` together.
    */
  trait RequestReaderFactory[Req] {
    def apply[Res](
      produces: Seq[(MediaType, Res => Res)]
    ): RequestReader[EndpointContext[Req, Res, Res]]
  }

  object NoBodyReaderFactory extends RequestReaderFactory[Unit] {
    override def apply[Res](
      produces: Seq[(MediaType, Res => Res)]
    ): RequestReader[EndpointContext[Unit, Res, Res]] = {
      RequestReaders.noBody(produces)
    }
  }

  object StandardReaderFactory extends RequestReaderFactory[String] {
    override def apply[Res](
      produces: Seq[(MediaType, Res => Res)]
    ): RequestReader[EndpointContext[String, Res, Res]] = {
      RequestReaders.standard(
        accepts = MediaTypes.applicationJson,
        produces = produces
      )
    }
  }

}
