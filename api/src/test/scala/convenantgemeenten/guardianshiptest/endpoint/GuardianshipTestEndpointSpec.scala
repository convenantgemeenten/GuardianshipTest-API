package convenantgemeenten.guardianshiptest.endpoint

import java.time.LocalDate

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import convenantgemeenten.guardianshiptest.ns.GuardianshipTest
import io.finch.{Application, Bootstrap, Input}
import lspace.codec
import lspace.codec.ActiveContext
import lspace.codec.argonaut.{nativeDecoder, nativeEncoder}
import lspace.codec.json.jsonld.JsonLDEncoder
import lspace.graphql.QueryResult
import lspace.ns.vocab.schema
import lspace.provider.detached.DetachedGraph
import lspace.provider.mem.MemGraph
import lspace.services.LApplication
import lspace.structure.Graph
import lspace.util.SampleGraph
import monix.eval.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterAll, FutureOutcome}
import shapeless.{:+:, CNil}

class GuardianshipTestEndpointSpec
    extends AsyncWordSpec
    with Matchers
    with BeforeAndAfterAll {

  import lspace.Implicits.Scheduler.global
  import lspace.encode.EncodeJson
  import lspace.encode.EncodeJson._
  import lspace.encode.EncodeJsonLD
  import lspace.encode.EncodeJsonLD._
  import lspace.services.codecs.Encode._

  lazy val dataGraph: Graph = MemGraph("dataGuardianshipTestEndpointSpec")
  lazy val testsDataGraph: Graph = MemGraph(
    "testsDataGuardianshipTestEndpointSpec")
  implicit val encoderJsonLD = JsonLDEncoder.apply(nativeEncoder)
  implicit val decoderJsonLD =
    lspace.codec.json.jsonld.JsonLDDecoder.apply(DetachedGraph)(nativeDecoder)
  implicit val decoderGraphQL = codec.graphql.Decoder
  import lspace.Implicits.AsyncGuide.guide
  implicit lazy val activeContext = GuardianshipTestEndpoint.activeContext

  val testsEndpoint =
    GuardianshipTestEndpoint(dataGraph,
                             testsDataGraph,
                             "http://example.org/guardianshiptest/")

  lazy val service: com.twitter.finagle.Service[Request, Response] = Bootstrap
    .configure(enableMethodNotAllowed = true, enableUnsupportedMediaType = true)
    .serve[LApplication.JsonLD :+: Application.Json :+: CNil](testsEndpoint.api)
    .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
      testsEndpoint.graphql)
    .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
      testsEndpoint.librarian)
    .toService

  lazy val initTask = (for {
    sample <- SampleGraph.loadSocial(dataGraph)
    _ <- for {
      curatele <- dataGraph.nodes.upsert(
        "curatele-gray",
        GuardianshipTestEndpoint.ns.LegalRestraint)
      _ <- sample.persons.Gray.person --- GuardianshipTestEndpoint.ns.underLegalRestraint --> curatele
      _ <- curatele --- schema.startDate --> LocalDate.parse("2016-01-02")
      _ <- curatele --- schema.endDate --> LocalDate.parse("2016-12-12")
      curatele2 <- dataGraph.nodes.upsert(
        "curatele-yoshio",
        GuardianshipTestEndpoint.ns.LegalRestraint)
      _ <- sample.persons.Yoshio.person --- GuardianshipTestEndpoint.ns.underLegalRestraint --> curatele2
      _ <- curatele2 --- schema.startDate --> LocalDate.parse("2018-01-02")
    } yield ()
  } yield sample).memoizeOnSuccess

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    new FutureOutcome(initTask.runToFuture flatMap { result =>
      super.withFixture(test).toFuture
    })
  }
  "A PartnerEndpoint" should {
    "test positive for a LegalConstraint for Gray" in {
      (for {
        sample <- initTask
        gray = sample.persons.Gray.person
        test = GuardianshipTest(gray.iri, Some(LocalDate.parse("2016-12-01")))
        node <- test.toNode
      } yield {
        val input = Input
          .post("/guardianship")
//          .withBody[LApplication.JsonLD](node)
          .withBody[Application.Json](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value
              .out(GuardianshipTest.keys.resultBoolean)
              .head shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test positive for a LegalConstraint for Yoshio" in {
      (for {
        sample <- initTask
        test = GuardianshipTest(sample.persons.Yoshio.person.iri)
        node <- test.toNode
      } yield {
        val input = Input
          .post("/guardianship")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value
              .out(GuardianshipTest.keys.resultBoolean)
              .head shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for a LegalConstraint for Stan" in {
      (for {
        sample <- initTask
        stan = sample.persons.Stan.person
        test = GuardianshipTest(stan.iri)
        node <- test.toNode
      } yield {
        val input = Input
          .post("/guardianship")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value
              .out(GuardianshipTest.keys.resultBoolean)
              .head shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for a LegalConstraint for Yoshio on 2015-01-01" in {
      (for {
        sample <- initTask
        test = GuardianshipTest(sample.persons.Yoshio.person.iri,
                                Some(LocalDate.parse("2015-01-01")))
        node <- test.toNode
      } yield {
        val input = Input
          .post("/guardianship")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value
              .out(GuardianshipTest.keys.resultBoolean)
              .head shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
  }
}
