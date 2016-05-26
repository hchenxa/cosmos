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

      "HTTP method parameter" - {

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

        def assertMethodMatch(routeMethod: Method, requestMethod: Method): Unit = {
          val endpoint = buildEndpoint(method = routeMethod)
          val request = buildRequest(method = requestMethod)

          assertSuccessfulRequest(endpoint, request)
        }

        def assertMethodMismatch(endpointMethod: Method, requestMethod: Method): Unit = {
          val endpoint = buildEndpoint(method = endpointMethod)
          val request = buildRequest(method = requestMethod)

          assertFailedRequest(endpoint, request)
        }

      }

      "path parameter" - {

        "route path foo/bar matches request path foo/bar" in {
          assertPathMatch(routePath = Seq("foo", "bar"), requestPath = Seq("foo", "bar"))
        }

        "route path foo/bar does not match request path foo/baz" in {
          assertPathMismatch(routePath = Seq("foo", "bar"), requestPath = Seq("foo", "baz"))
        }

        "route path foo/baz does not match request path foo/bar" in {
          assertPathMismatch(routePath = Seq("foo", "baz"), requestPath = Seq("foo", "bar"))
        }

        "route path foo/baz matches request path foo/baz" in {
          assertPathMatch(routePath = Seq("foo", "baz"), requestPath = Seq("foo", "baz"))
        }

        def assertPathMatch(routePath: Seq[String], requestPath: Seq[String]): Unit = {
          val endpoint = buildEndpoint(path = routePath)
          val request = buildRequest(path = requestPath)

          assertSuccessfulRequest(endpoint, request)
        }

        def assertPathMismatch(routePath: Seq[String], requestPath: Seq[String]): Unit = {
          val endpoint = buildEndpoint(path = routePath)
          val request = buildRequest(path = requestPath)

          assertFailedRequest(endpoint, request)
        }
      }

      def buildEndpoint(
        method: Method = Method.Post,
        path: Seq[String] = Seq.empty
      ): Endpoint[Json] = {
        val context = EndpointContext((), RequestSession(None), identity[Unit], MediaTypes.any)
        val reader = RequestReader.value(context)
        implicit val codec = EndpointCodec(reader, implicitly[Encoder[Unit]])

        val handler = new EndpointHandler {
          override def apply(request: Unit)(implicit session: RequestSession): Future[Unit] = {
            Future.value(())
          }
        }

        handler.testRoute(method, path: _*)
      }

      def buildRequest(method: Method = Method.Post, path: Seq[String] = Seq.empty): Request = {
        RequestBuilder()
          .url(s"http://some.host/${path.mkString("/")}")
          .build(method, content = None)
      }

      def assertSuccessfulRequest(endpoint: Endpoint[Json], request: Request): Unit = {
        inside(endpoint(Input(request))) { case Some((_, eval)) =>
          inside(Await.result(eval.value).value.as[Unit]) { case Xor.Right(result) =>
            assertResult(())(result)
          }
        }
      }

      def assertFailedRequest(endpoint: Endpoint[Json], request: Request): Unit = {
        assertResult(None)(endpoint(Input(request)))
      }

    }

  }

}
