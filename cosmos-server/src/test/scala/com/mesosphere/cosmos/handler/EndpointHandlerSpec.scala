package com.mesosphere.cosmos.handler

import cats.Eval
import cats.data.Xor
import cats.syntax.option._
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, MediaTypes, RequestSession}
import com.twitter.finagle.http.{Method, Request, RequestBuilder}
import com.twitter.util.{Await, Future}
import io.circe.{Decoder, Encoder, Json}
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
          val endpoint = buildEndpoint(buildRequestBodyHandler(()), method = routeMethod)
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
          val endpoint = buildEndpoint(buildRequestBodyHandler(()), path = routePath)
          val request = buildRequest(path = requestPath)

          callEndpoint(endpoint, request)
        }

      }

      "codec member" - {

        "requestReader" - {

          "requestBody is passed to apply" - {

            "int value" in {
              val result = callWithRequestBody(42)
              assertResponseBody(42, result)
            }

            "string value" in {
              val result = callWithRequestBody("hello world")
              assertResponseBody("hello world", result)
            }

            def callWithRequestBody[A](body: A)(implicit encoder: Encoder[A]): EndpointResult = {
              val endpoint = buildEndpoint(buildRequestBodyHandler(body))
              val request = buildRequest()

              callEndpoint(endpoint, request)
            }

          }

          "requestSession is passed to apply" - {
            "Some value" in {
              val result = callWithRequestSession(RequestSession(Some(Authorization("53cr37"))))
              assertResponseBody("53cr37".some, result)
            }
            "None" in {
              val result = callWithRequestSession(RequestSession(None))
              assertResponseBody(none[String], result)
            }

            def callWithRequestSession(session: RequestSession): EndpointResult = {
              val endpoint = buildEndpoint(buildRequestSessionHandler(session))
              val request = buildRequest()

              callEndpoint(endpoint, request)
            }
          }

        }

      }

      def buildRequestBodyHandler[A](requestBody: A)(implicit
        encoder: Encoder[A]
      ): EndpointHandler[A, A] = {
        val session = RequestSession(None)
        val context = EndpointContext(requestBody, session, identity[A], MediaTypes.any)
        val reader = RequestReader.value(context)
        implicit val codec = EndpointCodec(reader, encoder)

        new EndpointHandler {
          override def apply(request: A)(implicit session: RequestSession): Future[A] = {
            Future.value(request)
          }
        }
      }

      def buildRequestSessionHandler(
        session: RequestSession
      ): EndpointHandler[Unit, Option[String]] = {
        val context = EndpointContext((), session, identity[Option[String]], MediaTypes.any)
        val reader = RequestReader.value(context)
        implicit val codec = EndpointCodec(reader, implicitly[Encoder[Option[String]]])

        new EndpointHandler {
          override def apply(request: Unit)(implicit
            session: RequestSession
          ): Future[Option[String]] = {
            Future.value(session.authorization.map(_.token))
          }
        }
      }

      def buildEndpoint[Req, Res](
        handler: EndpointHandler[Req, Res],
        method: Method = Method.Post,
        path: Seq[String] = Seq.empty
      ): Endpoint[Json] = {
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

      def assertSuccessfulRequest(result: EndpointResult): Unit = assertResponseBody((), result)

      def assertResponseBody[A](expectedBody: A, endpointResult: EndpointResult)(implicit
        decoder: Decoder[A]
      ): Unit = {
        inside(endpointResult) { case Some((_, eval)) =>
          inside(Await.result(eval.value).value.as[A]) { case Xor.Right(responseBody) =>
            assertResult(expectedBody)(responseBody)
          }
        }
      }

      def assertFailedRequest(result: EndpointResult): Unit = assertResult(None)(result)

    }

  }

}
