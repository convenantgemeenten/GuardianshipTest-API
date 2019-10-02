package convenantgemeenten.guardianshiptest.service

object HCConfig {
  import pureconfig._
  import pureconfig.generic.auto._

  private val baseconfigspath = Option(
    System.getenv("GUARDIANSHIPTEST_CONFIGS_PATH"))
}
case class Sensitive(value: String) extends AnyVal {
  override def toString: String = "MASKED"
}
case class HCConfig(url: String, xApiKey: Sensitive)
