package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.Await

final class RequestReadersSpec extends UnitSpec {

  "Properties shared by all request readers" - {

    "Result contains Authorization if the request did" in {
      val request = RequestBuilder()
        .url("http://some.host")
        .setHeader("Authorization", "53cr37")
        .buildGet()

      val reader = RequestReaders.testBaseReader[Unit](Seq.empty)
      val (requestSession, _, _) = Await.result(reader(request))
      assertResult(RequestSession(Some(Authorization("53cr37"))))(requestSession)
    }
  }

}
