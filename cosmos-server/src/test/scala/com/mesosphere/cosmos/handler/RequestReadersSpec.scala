package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.Await

final class RequestReadersSpec extends UnitSpec {

  import RequestReadersSpec._

  "Properties shared by all request readers" - {

    "Result contains Authorization if the request did" in {
      val Some(requestSession) = runReader(authorization = Some("53cr37"))
      assertResult(RequestSession(Some(Authorization("53cr37"))))(requestSession)
    }

    "Result omits Authorization if the request did" in {
      val Some(requestSession) = runReader(authorization = None)
      assertResult(RequestSession(None))(requestSession)
    }

    "Fails if the Accept header is missing" in {
      val result = runReader(accept = None)
      assert(result.isEmpty)
    }

    "Fails if the Accept header is not a MediaType" in {
      val result = runReader(accept = Some("---not-a-media-type---"))
      assert(result.isEmpty)
    }

  }

}

object RequestReadersSpec {

  def runReader(
    accept: Option[String] = Some("application/json"),
    authorization: Option[String] = None
  ): Option[RequestSession] = {
    val request = RequestBuilder()
      .url("http://some.host")
      .setHeader("Accept", accept.toSeq)
      .setHeader("Authorization", authorization.toSeq)
      .buildGet()

    val reader = RequestReaders.testBaseReader[Unit](Seq.empty)
    Await.result(reader(request).liftToTry)
      .map { case (requestSession, _, _) => requestSession }
      .toOption
  }

}
