package convenantgemeenten.guardianshiptest.ns

import java.time.{Instant, LocalDate}

import convenantgemeenten.ns.Test
import lspace.Label
import lspace.Label.D._
import lspace.ns.vocab.schema
import lspace.provider.detached.DetachedGraph
import lspace.structure._
import monix.eval.Task

object GuardianshipTest
    extends OntologyDef(
      "https://ns.convenantgemeenten.nl/GuardianshipTest",
      label = "Guardianship test",
      comment =
        "An guardianship test is an assertion whether a person is under guardianship at a certain date.",
      labels = Map("nl" -> "Curatele toets"),
      comments = Map(
        "nl" -> "Een curatele-toets is een toetst of een subject onder curatele staat op een bepaalde datum.")
    ) {
  object keys extends Test.Properties {
    object subject
        extends PropertyDef(ontology.iri + "/subject",
                            label = "subject",
                            `@range` = () => schema.Thing.ontology :: Nil)

    object targetDate
        extends PropertyDef("https://ns.convenantgemeenten.nl/targetDate",
                            label = "targetDate",
                            `@range` = () => `@date` :: Nil)
    lazy val targetDateDate = targetDate as `@date`

    object result
        extends PropertyDef(
          ontology.iri + "/result",
          label = "result",
          `@extends` = () => Property("https://schema.org/result") :: Nil,
          `@range` = () => Label.D.`@boolean` :: Nil
        )
    lazy val resultBoolean
      : TypedProperty[Boolean] = result as Label.D.`@boolean`
  }
  override lazy val properties
    : List[Property] = keys.subject.property :: keys.result.property :: Test.properties
  trait Properties extends Test.Properties {
    lazy val subject = keys.subject
    lazy val targetDate = keys.targetDate
    lazy val targetDateDate = keys.targetDateDate
    lazy val result = keys.result
    lazy val resultBoolean = keys.resultBoolean
  }

  def fromNode(node: Node): GuardianshipTest = {
    GuardianshipTest(
      node.outE(keys.subject.property).head.to.iri,
      node.out(keys.targetDateDate).headOption,
      node.out(keys.executedOn as lspace.Label.D.`@datetime`).headOption,
      node.out(keys.resultBoolean).headOption,
      if (node.iri.nonEmpty) Some(node.iri) else None
    )
  }

  implicit def toNode(cc: GuardianshipTest): Task[Node] = {
    for {
      node <- cc.id
        .map(DetachedGraph.nodes.upsert(_, ontology))
        .getOrElse(DetachedGraph.nodes.create(ontology))
      person <- DetachedGraph.nodes.upsert(cc.subject,
                                           Set[String](),
                                           schema.Thing)
      _ <- node --- keys.subject.property --> person
      _ <- cc.targetDate
        .map(result => node --- keys.targetDate --> result)
        .getOrElse(Task.unit)
      _ <- cc.executedOn
        .map(node --- keys.executedOn --> _)
        .getOrElse(Task.unit)
      _ <- cc.result
        .map(result => node --- keys.result --> result)
        .getOrElse(Task.unit)
    } yield node
  }
}

case class GuardianshipTest(subject: String,
                            targetDate: Option[LocalDate] = None,
                            executedOn: Option[Instant] = None,
                            result: Option[Boolean] = None,
                            id: Option[String] = None) {
  lazy val toNode: Task[Node] = this
}
