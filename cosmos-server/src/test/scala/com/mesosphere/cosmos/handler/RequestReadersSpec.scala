package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, MediaType, MediaTypes, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.{Await, Return, Try}

final class RequestReadersSpec extends UnitSpec {

  import RequestReadersSpec._

  "Properties shared by all request readers" - {

    "Result contains Authorization if the request did" in {
      val Return(requestSession) = runReader(authorization = Some("53cr37"))
      assertResult(RequestSession(Some(Authorization("53cr37"))))(requestSession)
    }

    "Result omits Authorization if the request did" in {
      val Return(requestSession) = runReader(authorization = None)
      assertResult(RequestSession(None))(requestSession)
    }

    "Fails if the Accept header is missing" in {
      val result = runReader(accept = None)
      assert(result.isThrow)
    }

    "Fails if the Accept header is not a MediaType" in {
      val result = runReader(accept = Some("---not-a-media-type---"))
      assert(result.isThrow)
    }

    "Fails if the Accept header is not compatible with a MediaType in `produces`" - {
      "empty `produces`" in {
        val result = runReader(produces = Seq.empty)
        assert(result.isThrow)
      }
      "incompatible header value" in {
        val result = runReader(accept = Some("text/plain"))
        assert(result.isThrow)
      }
    }

  }

}

object RequestReadersSpec {

  def runReader(
    accept: Option[String] = Some(MediaTypes.applicationJson.show),
    authorization: Option[String] = None,
    produces: Seq[(MediaType, Unit => Unit)] = Seq((MediaTypes.applicationJson, identity))
  ): Try[RequestSession] = {
    val request = RequestBuilder()
      .url("http://some.host")
      .setHeader("Accept", accept.toSeq)
      .setHeader("Authorization", authorization.toSeq)
      .buildGet()

    val reader = RequestReaders.testBaseReader[Unit](produces)
    Await.result(reader(request).liftToTry)
      .map { case (requestSession, _, _) => requestSession }
  }

}
