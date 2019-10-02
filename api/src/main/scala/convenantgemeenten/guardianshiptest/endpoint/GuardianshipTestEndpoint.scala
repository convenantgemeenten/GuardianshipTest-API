package convenantgemeenten.guardianshiptest.endpoint

import java.time.{Instant, LocalDate}

import cats.effect.IO
import com.softwaremill.sttp.okhttp.monix.OkHttpMonixBackend
import convenantgemeenten.guardianshiptest.ns.GuardianshipTest
import io.finch._
import lspace.Label.D._
import lspace._
import lspace.codec._
import lspace.codec.json.jsonld.JsonLDDecoder
import lspace.decode.{DecodeJson, DecodeJsonLD}
import lspace.librarian.task.AsyncGuide
import lspace.ns.vocab.schema
import lspace.services.rest.endpoints.util.MatchParam
import lspace.services.rest.endpoints.{GraphqlApi, LabeledNodeApi, LibrarianApi}
import monix.eval.Task
import monix.execution.Scheduler
import shapeless.{:+:, CNil, HNil}

import scala.collection.immutable.ListMap

object GuardianshipTestEndpoint {
  def apply[Json](ageGraph: Graph, ageTestGraph: Graph, baseUrl: String = "")(
      implicit activeContext: ActiveContext = ActiveContext(),
      decoderJsonLD: JsonLDDecoder[Json],
      ecoderGraphQL: codec.graphql.Decoder,
      guide: AsyncGuide,
      scheduler: Scheduler): GuardianshipTestEndpoint[Json] =
    new GuardianshipTestEndpoint(ageGraph, ageTestGraph, baseUrl)

  lazy val activeContext = ActiveContext(
    `@prefix` = ListMap(
      "subject" -> GuardianshipTest.keys.subject.iri,
      "validOn" -> GuardianshipTest.keys.targetDate.iri,
      "executedOn" -> GuardianshipTest.keys.executedOn.iri,
      "result" -> GuardianshipTest.keys.result.iri
    ),
    definitions = Map(
      GuardianshipTest.keys.subject.iri -> ActiveProperty(
        `@type` = schema.Person :: Nil,
        property = GuardianshipTest.keys.subject)(),
      GuardianshipTest.keys.targetDate.iri -> ActiveProperty(
        `@type` = `@date` :: Nil,
        property = GuardianshipTest.keys.targetDate)(),
      GuardianshipTest.keys.executedOn.iri -> ActiveProperty(
        `@type` = `@datetime` :: Nil,
        property = GuardianshipTest.keys.executedOn)(),
      GuardianshipTest.keys.result.iri -> ActiveProperty(
        `@type` = `@boolean` :: Nil,
        property = GuardianshipTest.keys.result)()
    )
  )

  object ns {
    val underLegalRestraint = Property.properties.getOrCreate(
      "http://ns.convenantgemeenten.nl/underLegalRestraint")
    val LegalRestraint = Ontology.ontologies.getOrCreate(
      "http://ns.convenantgemeenten.nl/LegalRestraint")
  }
}

class GuardianshipTestEndpoint[Json](ageGraph: Graph,
                                     ageTestGraph: Graph,
                                     baseUrl: String)(
    implicit activeContext: ActiveContext = ActiveContext(),
    decoderJsonLD: JsonLDDecoder[Json],
    ecoderGraphQL: codec.graphql.Decoder,
    guide: AsyncGuide,
    scheduler: Scheduler)
    extends Endpoint.Module[IO] {

  import lspace.services.codecs.Decode._

  lazy val nodeApi =
    LabeledNodeApi(ageTestGraph, GuardianshipTest.ontology, baseUrl)
  lazy val librarianApi = LibrarianApi(ageTestGraph)
  lazy val graphQLApi = GraphqlApi(ageTestGraph)

  lazy val create: Endpoint[IO, Node] = {
    implicit val bodyJsonldTyped = DecodeJsonLD
      .bodyJsonldTyped(GuardianshipTest.ontology, GuardianshipTest.fromNode)

    implicit val jsonToNodeToT = DecodeJson
      .jsonToNodeToT(GuardianshipTest.ontology, GuardianshipTest.fromNode)

    post(body[
      Task[GuardianshipTest],
      lspace.services.codecs.Application.JsonLD :+: Application.Json :+: CNil])
      .mapOutputAsync {
        case task =>
          task
            .flatMap {
              case guardianshipTest: GuardianshipTest
                  if guardianshipTest.result.isDefined || guardianshipTest.id.isDefined =>
                Task.now(
                  NotAcceptable(
                    new Exception("result or id should not yet be defined")))
              case guardianshipTest: GuardianshipTest =>
                val now = Instant.now()
                (for {
                  result <- executeTest(guardianshipTest)
                    .onErrorHandle { f =>
                      false
                    }
                  testAsNode <- guardianshipTest
                    .copy(executedOn = Some(now),
                          result = Some(result),
                          id = Some(
                            baseUrl + java.util.UUID
                              .randomUUID()
                              .toString + scala.math.random()))
                    .toNode
                  persistedNode <- ageTestGraph.nodes ++ testAsNode
                } yield {
                  Ok(persistedNode).withHeader("Location" -> persistedNode.iri)
                }).onErrorHandle {
                  case f: Exception => InternalServerError(f)
                }
              case _ =>
                Task.now(NotAcceptable(new Exception("invalid parameters")))
            }
            .to[IO]
      }
  }

  def executeTest(guardianshipTest: GuardianshipTest): Task[Boolean] = {
    val targetDate = guardianshipTest.targetDate
      .getOrElse(LocalDate.now())
    g.N
      .hasIri(guardianshipTest.subject)
      .where(
        _.out(GuardianshipTestEndpoint.ns.underLegalRestraint)
          .hasLabel(GuardianshipTestEndpoint.ns.LegalRestraint)
          .has(schema.startDate, P.lt(targetDate))
          .or(
            _.has(schema.endDate, P.gt(targetDate)),
            _.hasNot(schema.endDate)
          ))
      .head()
      .withGraph(ageGraph)
      .headOptionF
      .map(_.isDefined)
  }

  lazy val api = nodeApi.context :+: nodeApi.byId :+: nodeApi.list :+: create :+: nodeApi.removeById
  lazy val graphql = MatchParam[IO]("query") :: graphQLApi.list(
    GuardianshipTest.ontology)
  lazy val librarian = librarianApi.filtered.list(GuardianshipTest.ontology)
}
