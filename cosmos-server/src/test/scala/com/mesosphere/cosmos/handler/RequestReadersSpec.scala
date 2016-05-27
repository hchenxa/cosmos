package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.util.Await

final class RequestReadersSpec extends UnitSpec {

  import RequestReadersSpec._

  "Properties shared by all request readers" - {

    "Result contains Authorization if the request did" in {
      val requestSession = runReader(authorization = Some("53cr37"))
      assertResult(RequestSession(Some(Authorization("53cr37"))))(requestSession)
    }

    "Result omits Authorization if the request did" in {
      val requestSession = runReader(authorization = None)
      assertResult(RequestSession(None))(requestSession)
    }
  }

}

object RequestReadersSpec {

  def runReader(authorization: Option[String]): RequestSession = {
    val baseRequestBuilder = RequestBuilder().url("http://some.host")
    val builderWithAuth = authorization match {
      case Some(auth) => baseRequestBuilder.setHeader("Authorization", auth)
      case _ => baseRequestBuilder
    }
    val request = builderWithAuth.buildGet()

    val reader = RequestReaders.testBaseReader[Unit](Seq.empty)
    val (requestSession, _, _) = Await.result(reader(request))
    requestSession
  }

}
