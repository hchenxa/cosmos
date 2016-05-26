package com.mesosphere.cosmos.handler

import cats.Eval
import cats.data.Xor
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.twitter.finagle.http.{Method, Request, RequestBuilder}
import com.twitter.util.{Await, Future}
import io.circe.{Encoder, Json}
import io.finch.{Endpoint, Input, Output, RequestReader}

final class EndpointHandlerSpec extends UnitSpec {

  "EndpointHandler" - {

    "route()" - {

      type EndpointResult = Option[(Input, Eval[Future[Output[Json]]])]

      "HTTP method parameter" - {

        "route get matches request get" in {
          val result = callWithMethods(routeMethod = Method.Get, requestMethod = Method.Get)
          assertSuccessfulRequest(result)
        }

        "route get does not match request post" in {
          val result = callWithMethods(routeMethod = Method.Get, requestMethod = Method.Post)
          assertFailedRequest(result)
        }

        "route post does not match request get" in {
          val result = callWithMethods(routeMethod = Method.Post, requestMethod = Method.Get)
          assertFailedRequest(result)
        }

        "route post matches request post" in {
          val result = callWithMethods(routeMethod = Method.Post, requestMethod = Method.Post)
          assertSuccessfulRequest(result)
        }

        def callWithMethods(routeMethod: Method, requestMethod: Method): EndpointResult = {
          val endpoint = buildEndpoint(method = routeMethod)
          val request = buildRequest(method = requestMethod)

          callEndpoint(endpoint, request)
        }

      }

      "path parameter" - {

        "route path foo/bar matches request path foo/bar" in {
          val result = callWithPaths(routePath = Seq("foo", "bar"), requestPath = Seq("foo", "bar"))
          assertSuccessfulRequest(result)
        }

        "route path foo/bar does not match request path foo/baz" in {
          val result = callWithPaths(routePath = Seq("foo", "bar"), requestPath = Seq("foo", "baz"))
          assertFailedRequest(result)
        }

        "route path foo/baz does not match request path foo/bar" in {
          val result = callWithPaths(routePath = Seq("foo", "baz"), requestPath = Seq("foo", "bar"))
          assertFailedRequest(result)
        }

        "route path foo/baz matches request path foo/baz" in {
          val result = callWithPaths(routePath = Seq("foo", "baz"), requestPath = Seq("foo", "baz"))
          assertSuccessfulRequest(result)
        }

        def callWithPaths(routePath: Seq[String], requestPath: Seq[String]): EndpointResult = {
          val endpoint = buildEndpoint(path = routePath)
          val request = buildRequest(path = requestPath)

          callEndpoint(endpoint, request)
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

      def callEndpoint(endpoint: Endpoint[Json], request: Request): EndpointResult = {
        endpoint(Input(request))
      }

      def assertSuccessfulRequest(result: EndpointResult): Unit = {
        inside(result) { case Some((_, eval)) =>
          inside(Await.result(eval.value).value.as[Unit]) { case Xor.Right(responseBody) =>
            assertResult(())(responseBody)
          }
        }
      }

      def assertFailedRequest(result: EndpointResult): Unit = assertResult(None)(result)

    }

  }

}
