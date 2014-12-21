package main.scala

import java.io.{PrintWriter, File}
import htsjdk.samtools.reference.ReferenceSequenceFileFactory
import org.slf4j._

/**
 * created by aaronmck on 12/16/14
 *
 * Copyright (c) 2014, aaronmck
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. 
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. 
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 *
 */
object Main extends App {
  // parse the command line arguments
  val parser = new scopt.OptionParser[Config]("DeepFry") {
    head("DeepFry", "1.0")

    // *********************************** Inputs *******************************************************
    opt[File]("knownCrisprBed") required() valueName ("<file>") action {
      (x, c) => c.copy(genomeCRISPRs = Some(x))
    } text ("the BED file containing the location of all the CRISPRs within the target genome")

    opt[File]("targetCrisprBeds") required() valueName ("<file>") action {
      (x, c) => c.copy(targetBed = Some(x))
    } text ("the output file of guides with a minimium number of hits")

    opt[File]("reference") required() valueName ("<file>") action {
      (x, c) => c.copy(reference = Some(x))
    } text ("the reference file to pull sequence from")

    opt[File]("output") required() valueName ("<file>") action {
      (x, c) => c.copy(output = Some(x))
    } text ("the output file")

    opt[Boolean]("noOffTarget") action {
      (x, c) => c.copy(scoreOffTarget = !(x))
    } text ("Do not compute off-target hits (a large cost)")

    opt[Boolean]("noOnTarget") action {
      (x, c) => c.copy(scoreOffTarget = !(x))
    } text ("Do not compute on-target hits (a small savings at best)")

    // some general command-line setup stuff
    note("Find CRISPR targets across the specified genome\n")
    help("help") text ("prints the usage information you see here")
  }
  val logger = LoggerFactory.getLogger("Main")

  parser.parse(args, Config()) map {
    config => {
      // load up the list of known hits in the genome
      println("Loading the Trie from " + config.genomeCRISPRs.get.getAbsolutePath)
      val trie = CRISPRPrefixMap.fromBed(config.genomeCRISPRs.get.getAbsolutePath,true)

      // load up the reference file
      val fasta = ReferenceSequenceFileFactory.getReferenceSequenceFile(config.reference.get)

      // score each hit for uniqueness and optimality
      println("Loading the target regions from " + config.targetBed.get.getAbsolutePath)
      val targetRegions = new BEDFile(config.targetBed.get)

      // the output file
      val outputFile = new PrintWriter(config.output.get)

      println("Scorring sites...")
      var scoredSites = 0
      // output each target region -> hit combination for further analysis
      targetRegions.foreach { bedEntry => {
        if (bedEntry.isDefined) {
          //println("scoring site " + bedEntry.get.contig + ":" + bedEntry.get.start)
          val trueEntry = bedEntry.get

          // get the target sequence
          val targetSeq = fasta.getSubsequenceAt(trueEntry.contig, trueEntry.start, trueEntry.stop).getBases.map { case (b) => b.toChar}.mkString

          // find and CRISPRs in the target region
          val CRISPRs = CRISPRFinder.findCRISPRSites(targetSeq, trueEntry.contig, trueEntry.start, trie.CRISPRLength.getOrElse(0))

          // grab the bed entry and score it for off-target and on-target hits
          //println("Scoring")
          CRISPRs.foreach { CRISPR => {
            var outputCRISPR = CRISPR
            if (config.scoreOffTarget)
              outputCRISPR = BedEntry.append(outputCRISPR, Array[String]("t=" + CRISPR.name.get + ";f=" + CRISPRPrefixMap.totalScore(trie.score(ScoreHit.zipAndExpand(outputCRISPR.name.get)))))
            if (config.scoreOnTarget)
              outputCRISPR = BedEntry.append(outputCRISPR, Array[String]("t=" + CRISPR.name.get + ";o=" + CRISPRPrefixMap.totalScore(trie.score(ScoreHit.zipAndExpand(outputCRISPR.name.get)))))
            outputFile.write(outputCRISPR + "\n")
          }}
          scoredSites += 1
          if (scoredSites % 100 == 0) println("Scored sites " + scoredSites)
        }
      }}
      outputFile.close()
    }
  }
}

/*
 * the configuration class, it stores the user's arguments from the command line, set defaults here
 */
case class Config(genomeCRISPRs: Option[File] = None,
                  targetBed: Option[File] = None,
                  reference: Option[File] = None,
                  output: Option[File] = None,
                  scoreOffTarget: Boolean = true,
                  scoreOnTarget: Boolean = true)