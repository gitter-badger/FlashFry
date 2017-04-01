package modules

import java.io.File

import bitcoding.BitEncoding
import com.typesafe.scalalogging.LazyLogging
import scoring.ScoringManager
import standards.ParameterPack
import targetio.{TargetInput, TargetOutput}

/**
  * Given a results bed file from off-target discovery, annotate it with scores using established scoring schemes
  */
class ScoreResults(args: Array[String]) extends LazyLogging {

  // parse the command line arguments
  val parser = new ScoreBaseOptions()

  parser.parse(args, ScoreConfig()) map {
    config => {

      // get our settings
      val params = ParameterPack.nameToParameterPack(config.enzyme)

      // make ourselves a bit encoder
      val bitEnc = new BitEncoding(params)

      // load up the scored sites into a container
      val posEncoderAndOffTargets = TargetInput.inputBedToTargetArray(new File(config.inputBED),bitEnc, 2000)

      // get a scoring manager
      val scoringManager = new ScoringManager(bitEnc,posEncoderAndOffTargets._1,config.scoringMetrics,args)

      // score all the sites
      val newGuides = scoringManager.scoreGuides(posEncoderAndOffTargets._2)

      // output a new data file with the scored results
      TargetOutput.output(config.outputBED,newGuides,true,false,bitEnc,posEncoderAndOffTargets._1, scoringManager.scoringAnnotations)

    }
  }
}


/*
 * the configuration class, it stores the user's arguments from the command line, set defaults here
 */
case class ScoreConfig(analysisType: Option[String] = None,
                       inputBED: String = "",
                       outputBED: String = "",
                       enzyme: String = "spCas9",
                       scoringMetrics: Seq[String] = Seq())


class ScoreBaseOptions extends scopt.OptionParser[ScoreConfig]("DiscoverOTSites") {
  // *********************************** Inputs *******************************************************
  opt[String]("analysis") required() valueName ("<string>") action {
    (x, c) => c.copy(analysisType = Some(x))
  } text ("The run type: one of: discovery, score")

  // *********************************** Inputs *******************************************************
  opt[String]("inputBED") required() valueName ("<string>") action { (x, c) => c.copy(inputBED = x) } text ("the reference file to scan for putitive targets")
  opt[String]("outputBED") required() valueName ("<string>") action { (x, c) => c.copy(outputBED = x) } text ("the output file (in bed format)")
  opt[Seq[String]]("scoringMetrics") required() valueName("<scoringMethod1>,<scoringMethod1>...") action{ (x,c) => c.copy(scoringMetrics = x) } text ("scoring methods to include")
  opt[String]("enzyme") valueName ("<string>") action { (x, c) => c.copy(enzyme = x) } text ("which enzyme to use (cpf1, spCas9)")

  // some general command-line setup stuff
  note("match off-targets for the specified guides to the genome of interest\n")
  help("help") text ("match off-targets for the specified guides to the genome of interest\n")
}