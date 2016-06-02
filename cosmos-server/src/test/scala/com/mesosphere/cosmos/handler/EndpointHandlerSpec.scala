package com.mesosphere.cosmos.handler

import cats.Eval
import cats.data.Xor
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.{Authorization, MediaType, MediaTypes, RequestSession}
import com.twitter.finagle.http.Method._
import com.twitter.finagle.http.{Method, RequestBuilder, Status}
import com.twitter.util.{Await, Future}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import io.finch._
import shapeless.HNil

final class EndpointHandlerSpec extends UnitSpec {

  "EndpointHandler" - {

    "forRoute()" - {

      "method portion of route parameter" - {

        "route get matches request get" in {
          val result = callWithMethods(routeMethod = Method.Get, requestMethod = Method.Get)
          assert(result.isDefined)
        }

        "route get does not match request post" in {
          val result = callWithMethods(routeMethod = Method.Get, requestMethod = Method.Post)
          assert(result.isEmpty)
        }

        "route post does not match request get" in {
          val result = callWithMethods(routeMethod = Method.Post, requestMethod = Method.Get)
          assert(result.isEmpty)
        }

        "route post matches request post" in {
          val result = callWithMethods(routeMethod = Method.Post, requestMethod = Method.Post)
          assert(result.isDefined)
        }

        def callWithMethods(routeMethod: Method, requestMethod: Method): EndpointResult = {
          callEndpoint(
            handler = buildRequestBodyHandler(requestBody = ()),
            route = methodFn(routeMethod)(/),
            requestMethod = requestMethod
          )
        }

        def methodFn[A](method: Method): Endpoint[A] => Endpoint[A] = {
          method match {
            case Get     => get
            case Post    => post
            case Put     => put
            case Head    => head
            case Patch   => patch
            case Delete  => delete
            case Trace   => trace
            case Connect => connect
            case Options => options
            case _       => throw new AssertionError("unknown HTTP method")
          }
        }

      }

      "path portion of route parameter" - {

        "route path foo/bar matches request path foo/bar" in {
          val result = callWithPaths(routePath = Seq("foo", "bar"), requestPath = Seq("foo", "bar"))
          assert(result.isDefined)
        }

        "route path foo/bar does not match request path foo/baz" in {
          val result = callWithPaths(routePath = Seq("foo", "bar"), requestPath = Seq("foo", "baz"))
          assert(result.isEmpty)
        }

        "route path foo/baz does not match request path foo/bar" in {
          val result = callWithPaths(routePath = Seq("foo", "baz"), requestPath = Seq("foo", "bar"))
          assert(result.isEmpty)
        }

        "route path foo/baz matches request path foo/baz" in {
          val result = callWithPaths(routePath = Seq("foo", "baz"), requestPath = Seq("foo", "baz"))
          assert(result.isDefined)
        }

        def callWithPaths(routePath: Seq[String], requestPath: Seq[String]): EndpointResult = {
          val handler = buildRequestBodyHandler(requestBody = ())
          val route = post(routePath.foldLeft[Endpoint[HNil]](/)(_ / _))
          callEndpoint(handler, route = route, requestPath = requestPath)
        }

      }

      "codec member" - {

        "requestReader" - {

          "requestBody is passed to apply" - {

            "int value" in {
              val result = callEndpoint(buildRequestBodyHandler(requestBody = 42))
              val responseBody = extractBody[Int](result)
              assertResult(42)(responseBody)
            }

            "string value" in {
              val result = callEndpoint(buildRequestBodyHandler(requestBody = "hello world"))
              val responseBody = extractBody[String](result)
              assertResult("hello world")(responseBody)
            }

          }

          "requestSession is passed to apply" - {
            "Some value" in {
              val result = callWithRequestSession(RequestSession(Some(Authorization("53cr37"))))
              val responseBody = extractBody[Option[String]](result)
              assertResult(Some("53cr37"))(responseBody)
            }
            "None" in {
              val result = callWithRequestSession(RequestSession(None))
              val responseBody = extractBody[Option[String]](result)
              assertResult(None)(responseBody)
            }

            def callWithRequestSession(session: RequestSession): EndpointResult = {
              callEndpoint(buildRequestSessionHandler(session))
            }
          }

          "responseFormatter modifies the response" - {
            "plus three" in {
              val handler = buildRequestBodyHandler[Int](requestBody = 5, responseFormatter = _ + 3)
              val result = callEndpoint(handler)
              val responseBody = extractBody[Int](result)
              assertResult(8)(responseBody)
            }
            "times three" in {
              val handler = buildRequestBodyHandler[Int](requestBody = 5, responseFormatter = _ * 3)
              val result = callEndpoint(handler)
              val responseBody = extractBody[Int](result)
              assertResult(15)(responseBody)
            }
          }

          "responseContentType is sent with the response" - {
            "application/json" in {
              val result = callEndpoint(buildRequestBodyHandler(
                requestBody = (),
                responseContentType = MediaTypes.applicationJson
              ))

              val contentType = extractContentType(result)
              assertResult(MediaTypes.applicationJson.show)(contentType)
            }
            "text/plain" in {
              val result = callEndpoint(buildRequestBodyHandler(
                requestBody = (),
                responseContentType = MediaType("text", "plain")
              ))

              val contentType = extractContentType(result)
              assertResult(MediaType("text", "plain").show)(contentType)
            }
          }

        }

        "responseEncoder" - {
          "int" in {
            val encoder = Encoder.instance[Unit](_ => 42.asJson)
            val result = callEndpoint(buildRequestBodyHandler[Unit](())(encoder))
            val responseBody = extractBody[Int](result)
            assertResult(42)(responseBody)
          }
          "string" in {
            val encoder = Encoder.instance[Unit](_ => "hello world".asJson)
            val result = callEndpoint(buildRequestBodyHandler[Unit](())(encoder))
            val responseBody = extractBody[String](result)
            assertResult("hello world")(responseBody)
          }
        }

      }

      "apply method" - {
        "reverse input" in {
          assertApply(_.reverse, "dlrow olleh")
        }

        "uppercase input" in {
          assertApply(_.toUpperCase, "HELLO WORLD")
        }

        def assertApply(fn: String => String, expected: String): Unit = {
          implicit val codec = buildCodec[String, String]("hello world")

          val handler = new EndpointHandler {
            override def apply(request: String)(implicit
              session: RequestSession
            ): Future[String] = {
              Future.value(fn(request))
            }
          }

          val result = callEndpoint(handler)
          val responseBody = extractBody[String](result)
          assertResult(expected)(responseBody)
        }
      }

      "status code" in {
        val result = callEndpoint(buildRequestBodyHandler(requestBody = ()))
        val response = extractResponse(result)
        assertResult(Status.Ok)(response.status)
      }

      type EndpointResult = Option[(Input, Eval[Future[Output[Json]]])]

      def buildRequestBodyHandler[A](
        requestBody: A,
        responseFormatter: A => A = identity[A] _,
        responseContentType: MediaType = MediaTypes.any
      )(implicit encoder: Encoder[A]): EndpointHandler[A, A, A] = {
        implicit val codec = buildCodec(
          requestBody = requestBody,
          responseFormatter = responseFormatter,
          responseContentType = responseContentType
        )

        new EndpointHandler {
          override def apply(request: A)(implicit session: RequestSession): Future[A] = {
            Future.value(request)
          }
        }
      }

      def buildRequestSessionHandler(
        session: RequestSession
      ): EndpointHandler[Unit, Option[String], Option[String]] = {
        implicit val codec = buildCodec[Unit, Option[String]]((), session)

        new EndpointHandler {
          override def apply(request: Unit)(implicit
            session: RequestSession
          ): Future[Option[String]] = {
            Future.value(session.authorization.map(_.token))
          }
        }
      }

      def buildCodec[Req, Res](
        requestBody: Req,
        session: RequestSession = RequestSession(None),
        responseFormatter: Res => Res = identity[Res] _,
        responseContentType: MediaType = MediaTypes.any
      )(implicit encoder: Encoder[Res]): EndpointCodec[Req, Res, Res] = {
        val context = EndpointContext(requestBody, session, responseFormatter, responseContentType)
        val reader = RequestReader.value(context)
        EndpointCodec(reader, encoder)
      }

      def callEndpoint[Req, Res](
        handler: EndpointHandler[Req, Res, Res],
        route: Endpoint[HNil] = post(/),
        requestMethod: Method = Method.Post,
        requestPath: Seq[String] = Seq.empty
      ): EndpointResult = {
        val endpoint = handler.forRoute(route)
        val request = RequestBuilder()
          .url(s"http://some.host/${requestPath.mkString("/")}")
          .build(requestMethod, content = None)

        endpoint(Input(request))
      }

      def extractBody[A: Decoder](result: EndpointResult): A = {
        val response = extractResponse(result)
        val Xor.Right(body) = response.value.as[A]
        body
      }

      def extractContentType(result: EndpointResult): String = {
        val response = extractResponse(result)
        val Some(contentType) = response.contentType
        contentType
      }

      def extractResponse(result: EndpointResult): Output[Json] = {
        val Some((_, eval)) = result
        Await.result(eval.value)
      }

    }

  }

}
