package com.mesosphere.cosmos.handler

import cats.Eval
import cats.data.Xor
import cats.syntax.option._
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, MediaTypes, RequestSession}
import com.twitter.finagle.http.{Method, RequestBuilder}
import com.twitter.util.{Await, Future}
import io.circe.{Decoder, Encoder, Json}
import io.finch.{Input, Output, RequestReader}

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
          callEndpoint(
            handler = buildRequestBodyHandler(()),
            routeMethod = routeMethod,
            requestMethod = requestMethod
          )
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
          callEndpoint(buildRequestBodyHandler(()), routePath = routePath, requestPath = requestPath)
        }

      }

      "codec member" - {

        "requestReader" - {

          "requestBody is passed to apply" - {

            "int value" in {
              val result = callEndpoint(buildRequestBodyHandler(42))
              assertResponseBody(42, result)
            }

            "string value" in {
              val result = callEndpoint(buildRequestBodyHandler("hello world"))
              assertResponseBody("hello world", result)
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
              callEndpoint(buildRequestSessionHandler(session))
            }
          }

        }

      }

      def buildRequestBodyHandler[A](requestBody: A)(implicit
        encoder: Encoder[A]
      ): EndpointHandler[A, A] = {
        implicit val codec = buildCodec(requestBody, RequestSession(None))

        new EndpointHandler {
          override def apply(request: A)(implicit session: RequestSession): Future[A] = {
            Future.value(request)
          }
        }
      }

      def buildRequestSessionHandler(
        session: RequestSession
      ): EndpointHandler[Unit, Option[String]] = {
        implicit val codec = buildCodec[Unit, Option[String]]((), session)

        new EndpointHandler {
          override def apply(request: Unit)(implicit
            session: RequestSession
          ): Future[Option[String]] = {
            Future.value(session.authorization.map(_.token))
          }
        }
      }

      def buildCodec[Req, Res](requestBody: Req, session: RequestSession)(implicit
        encoder: Encoder[Res]
      ): EndpointCodec[Req, Res] = {
        val context = EndpointContext(requestBody, session, identity[Res], MediaTypes.any)
        val reader = RequestReader.value(context)
        EndpointCodec(reader, encoder)
      }

      def callEndpoint[Req, Res](
        handler: EndpointHandler[Req, Res],
        routeMethod: Method = Method.Post,
        routePath: Seq[String] = Seq.empty,
        requestMethod: Method = Method.Post,
        requestPath: Seq[String] = Seq.empty
      ): EndpointResult = {
        val endpoint = handler.testRoute(routeMethod, routePath: _*)
        val request = RequestBuilder()
          .url(s"http://some.host/${requestPath.mkString("/")}")
          .build(requestMethod, content = None)

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
