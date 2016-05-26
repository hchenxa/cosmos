package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.twitter.finagle.http.{Method, Request, RequestBuilder}
import com.twitter.util.{Await, Future}
import io.circe.{Encoder, Json}
import io.finch.{Endpoint, Input, RequestReader}

final class EndpointHandlerSpec extends UnitSpec {

  "EndpointHandler" - {

    "route()" - {

      "method parameter" - {

        "route get matches request get" in {
          assertMethodMatch(routeMethod = Method.Get, requestMethod = Method.Get)
        }

        "route get does not match request post" in {
          assertMethodMismatch(endpointMethod = Method.Get, requestMethod = Method.Post)
        }

        "route post does not match request get" in {
          assertMethodMismatch(endpointMethod = Method.Post, requestMethod = Method.Get)
        }

        "route post matches request post" in {
          assertMethodMatch(routeMethod = Method.Post, requestMethod = Method.Post)
        }

        def buildEndpoint(method: Method): Endpoint[Json] = {
          val context = EndpointContext((), RequestSession(None), identity[Unit], MediaTypes.any)
          val reader = RequestReader.value(context)
          implicit val codec = EndpointCodec(reader, implicitly[Encoder[Unit]])

          val handler = new EndpointHandler {
            override def apply(request: Unit)(implicit session: RequestSession): Future[Unit] = {
              Future.value(())
            }
          }

          handler.testRoute(method)
        }

        def buildRequest(method: Method): Request = {
          RequestBuilder().url("http://some.host/").build(method, content = None)
        }

        def assertMethodMatch(routeMethod: Method, requestMethod: Method): Unit = {
          val endpoint = buildEndpoint(routeMethod)
          val request = buildRequest(requestMethod)

          inside(endpoint(Input(request))) { case Some((_, eval)) =>
            inside(Await.result(eval.value).value.as[Unit]) { case Xor.Right(result) =>
              assertResult(())(result)
            }
          }
        }

        def assertMethodMismatch(endpointMethod: Method, requestMethod: Method): Unit = {
          val endpoint = buildEndpoint(endpointMethod)
          val request = buildRequest(requestMethod)

          assertResult(None)(endpoint(Input(request)))
        }

      }

    }

  }

}
