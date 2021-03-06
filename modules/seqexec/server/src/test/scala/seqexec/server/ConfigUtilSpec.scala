// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import edu.gemini.spModel.config2.{Config, DefaultConfig, ItemEntry, ItemKey}
import edu.gemini.spModel.seqcomp.SeqConfigNames
import seqexec.model.enum.SystemName
import org.scalacheck.{Arbitrary, _}
import org.scalacheck.Arbitrary._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import cats.implicits._

trait ConfigArbitraries {

  implicit val arbItemKey: Arbitrary[ItemKey] =
    Arbitrary {
      for {
        prefix <- Gen.oneOf(Seq(SeqConfigNames.OBSERVE_KEY, SeqConfigNames.CALIBRATION_KEY, SeqConfigNames.TELESCOPE_KEY, SeqConfigNames.INSTRUMENT_KEY, SeqConfigNames.META_DATA_KEY, SeqConfigNames.OCS_KEY))
        suffix <- arbitrary[String]
      } yield new ItemKey(prefix, suffix)
    }

  // Will generate ItemEntry with only String values
  implicit val arbItemEntry: Arbitrary[ItemEntry] =
    Arbitrary {
      for {
        key   <- arbitrary[ItemKey]
        value <- arbitrary[String]
      } yield new ItemEntry(key, value)
    }

  implicit val arbConfig: Arbitrary[Config] =
    Arbitrary {
      for {
        items <- arbitrary[Array[ItemEntry]]
      } yield new DefaultConfig(items)
    }
}

class ConfigUtilSpec extends FlatSpec with Matchers with EitherValues with PropertyChecks with ConfigArbitraries {
  import ConfigUtilOps._

  "ConfigUtil" should
    "extract keys with the correct type" in {
      forAll { (c: Config, k: ItemKey) =>
        // Make sure the key is present
        c.putItem(k, "value")
        c.extract(k).as[String] shouldBe Right("value")
      }
    }
    it should "fail to extract keys with the wrong type" in {
      forAll { (c: Config, k: ItemKey) =>
        c.putItem(k, "value")
        c.extract(k).as[Int].left.value should matchPattern {
          case ConversionError(_, _) =>
        }
      }
    }
    it should "fail to extract unknown keys" in {
      forAll { (c: Config, k: ItemKey) =>
        // Make sure the key is removed
        c.remove(k)
        c.extract(k).as[String].left.value should matchPattern {
          case KeyNotFound(_) =>
        }
      }
    }
    it should "convert to StepConfig" in {
      forAll { (c: Config) =>
        // Not much to check but at least verify the amount of subsystems
        val subsystems = c.getKeys.map(_.getRoot.getName).map(SystemName.unsafeFromString).distinct
        c.toStepConfig.keys should contain theSameElementsAs subsystems
      }
    }
}
