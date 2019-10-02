package convenantgemeenten.guardianshiptest.service

import java.time.Instant
import java.time.format.DateTimeFormatter

import cats.effect.IO
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.twitter.io.Buf
import com.twitter.server.TwitterServer
import convenantgemeenten.guardianshiptest.endpoint._
import convenantgemeenten.guardianshiptest.ns.GuardianshipTest
import io.finch.{
  Application,
  Bootstrap,
  Endpoint,
  InternalServerError,
  NotAcceptable,
  Ok,
  Text
}
import lspace._
import lspace.codec.json.jsonld.JsonLDEncoder
import lspace.codec.{ActiveContext, json}
import lspace.decode.{DecodeJson, DecodeJsonLD}
import lspace.encode.{EncodeJson, EncodeJsonLD}
import lspace.ns.vocab.schema
import lspace.provider.detached.DetachedGraph
import lspace.services.LService
import lspace.services.codecs.{Application => LApplication}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import shapeless.{:+:, CNil}

import scala.concurrent.Await
import scala.util.Try

object GuardianshipTestService extends LService with TwitterServer {
  lazy val guardianshipGraph: Graph = Graph("ageGraph")
  lazy val guardianshipTestGraph: Graph = Graph("ageTestGraph")

  import lspace.codec.argonaut._
  implicit val ec: Scheduler = lspace.Implicits.Scheduler.global

  implicit val encoderJsonLD = JsonLDEncoder.apply(nativeEncoder)
  implicit val decoderJsonLD =
    lspace.codec.json.jsonld.JsonLDDecoder.apply(DetachedGraph)(nativeDecoder)
  implicit val decoderGraphQL = codec.graphql.Decoder
  import lspace.Implicits.AsyncGuide.guide
  implicit lazy val activeContext = GuardianshipTestEndpoint.activeContext

  lazy val agetestEndpoint =
    GuardianshipTestEndpoint(
      guardianshipGraph,
      guardianshipTestGraph,
      "http://demo.convenantgemeenten.nl/guardianshiptest/") //TODO: get from config

  object UtilsApi extends Endpoint.Module[IO] {
    import io.finch._

    def reset(): Task[Unit] =
      for {
        _ <- { //partnerdataset
          import com.github.tototoshi.csv._
          import scala.io.Source

          val csvIri =
            "https://raw.githubusercontent.com/VNG-Realisatie/convenant-gemeenten/master/Documents/Testdata/Partnerdataset_Def.csv"
          val source = Source.fromURL(csvIri)

          implicit object MyFormat extends DefaultCSVFormat {
            override val delimiter = ','
          }
          val reader = CSVReader.open(source)

          val data = reader.allWithHeaders

          val formatter = DateTimeFormatter.ofPattern("M/d/yyyy")

          Observable
            .fromIterable(data)
            .map(_.filter(_._2.nonEmpty))
            .mapEval { record =>
              val ssn = record.get("bsn")
              for {
                person <- guardianshipGraph.nodes.upsert(
                  s"${guardianshipGraph.iri}/person/nl_${ssn.get}",
                  schema.Person)
                _ <- Task.gather {
                  Seq(
                    record
                      .get("ondercuratele")
                      .map(v =>
                        person --- GuardianshipTestEndpoint.ns.underLegalRestraint --> v)
                  ).flatten
                }
              } yield person
            }
            .onErrorHandle { f =>
              scribe.error(f.getMessage); throw f
            }
            .completedL
        }
      } yield ()

    val resetGraphs: Endpoint[IO, String] = get(path("reset")) {
      (for {
        _ <- purge()
        _ <- reset()
      } yield ()).runToFuture(monix.execution.Scheduler.global)

      Ok("resetting now, building graphs...")
    }

    def custompath(name: String) = path(name)
    def purge() = guardianshipGraph.purge
    val clearGraphs: Endpoint[IO, String] = get(path("clear")) {
      purge.startAndForget
        .runToFuture(monix.execution.Scheduler.global)
      Ok("clearing now")
    }

    val persist: Endpoint[IO, Unit] = get("_persist") {
      scribe.info("persisting all graphs")
      guardianshipGraph.persist
      io.finch.NoContent[Unit]
    }
  }

//  SampleData.loadSample(graph).runSyncUnsafe()(monix.execution.Scheduler.global, CanBlock.permit)
  UtilsApi.reset.runToFuture

  lazy val service: Service[Request, Response] = {

    import lspace.services.codecs.Encode._
    import EncodeJson._
    import EncodeJsonLD._
    import io.finch.Encode._
    import io.finch.ToResponse._
    import io.finch.fs2._

    Bootstrap
      .configure(enableMethodNotAllowed = true,
                 enableUnsupportedMediaType = false)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        agetestEndpoint.nodeApi.context :+: agetestEndpoint.nodeApi.byId :+: agetestEndpoint.nodeApi.list :+: agetestEndpoint.create :+: agetestEndpoint.nodeApi.removeById)
      //        agetestEndpoint.api)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        agetestEndpoint.graphql)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        agetestEndpoint.librarian)
      .serve[Text.Plain :+: CNil](
        UtilsApi.clearGraphs :+: UtilsApi.resetGraphs :+: UtilsApi.persist)
      .serve[Text.Html](App.appService.api)
      .toService
  }

  def main(): Unit = {
    val server = Http.server
    //      .configured(Stats(statsReceiver))
      .serve(
        ":8080",
        service
      )

    import lspace.services.util._
    import scala.concurrent.duration._
    onExit {
      println(s"close age-test-server")
      Await.ready(
        Task
          .sequence(Seq(
            Task.gatherUnordered(Seq(
              guardianshipGraph.persist,
              guardianshipTestGraph.persist
            )),
            Task.gatherUnordered(Seq(
              guardianshipGraph.close,
              guardianshipTestGraph.close
            ))
          ))
          .runToFuture(monix.execution.Scheduler.global),
        20 seconds
      )

      server.close()
    }

    com.twitter.util.Await.ready(adminHttpServer)
  }
}
