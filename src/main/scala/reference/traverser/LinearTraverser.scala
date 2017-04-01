package reference.traverser

import java.io._
import java.nio.ByteBuffer
import java.nio.channels.{Channels, FileChannel, SeekableByteChannel}
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import bitcoding.{BitEncoding, BitPosition}
import com.typesafe.scalalogging.LazyLogging
import crispr.CRISPRSiteOT
import htsjdk.samtools.util.{BlockCompressedFilePointerUtil, BlockCompressedInputStream, BlockGunzipper}
import utils.{BaseCombinationGenerator, Utils}
import reference.binary.{BinaryHeader, BlockOffset}
import reference.traversal.{BinToGuidesLookup, BinTraversal}
import standards.ParameterPack

import scala.collection.mutable

/**
  * traverse a binary database file, seeking to the correct bins
  */
object LinearTraverser extends Traverser with LazyLogging {

  /**
    * scan against the binary database of off-target sites in an implmenetation specific way
    *
    * @param binaryFile    the file we're scanning from
    * @param header        we have to parse the header ahead of time so that we know
    * @param traversal     the traversal over bins we'll use
    * @param targets       the array of candidate guides we have
    * @param maxMismatch   how many mismatches we support
    * @param configuration our enzyme configuration
    * @param bitCoder      our bit encoder
    * @param posCoder      the position encoder
    * @return a guide to OT hit array
    */
  def scan(binaryFile: File,
           header: BinaryHeader,
           traversal: BinTraversal,
           targets: Array[CRISPRSiteOT],
           maxMismatch: Int,
           configuration: ParameterPack,
           bitCoder: BitEncoding,
           posCoder: BitPosition): Array[CRISPRSiteOT] = {

    val formatter = java.text.NumberFormat.getInstance()

    val blockCompressedInput = new BlockCompressedInputStream(binaryFile)

    // setup our input file
    val filePath = Paths.get(binaryFile.getAbsolutePath)
    val channel = FileChannel.open(filePath, StandardOpenOption.READ)
    val inputStream = Channels.newInputStream(channel)

    // where we collect the off-target hits
    val siteSequenceToSite = new mutable.LinkedHashMap[Long, CRISPRSiteOT]()
    val guideList = new Array[Long](targets.size)

    targets.zipWithIndex.foreach { case (tgt, index) => {
      guideList(index) = tgt.longEncoding
      siteSequenceToSite(tgt.longEncoding) = tgt
    }
    }

    var t0 = System.nanoTime()
    var binIndex = 0

    // ------------------------------------------ traversal ------------------------------------------
    traversal.foreach { guidesToSeekForBin => {
      assert(header.blockOffsets contains guidesToSeekForBin.bin)

      val binPositionInformation = header.blockOffsets(guidesToSeekForBin.bin)

      val longBuffer = fillBlock(blockCompressedInput, binPositionInformation, new File(binaryFile.getAbsolutePath), guidesToSeekForBin.bin, bitCoder)

      //Traverser.validateBlock(longBuffer,binPositionInformation.numberOfTargets,binPositionInformation.uncompressedSize / 8,bitCoder,guidesToSeekForBin.bin)

      Traverser.compareBlock(longBuffer,
        binPositionInformation.numberOfTargets,
        guidesToSeekForBin.guides,
        bitCoder,
        maxMismatch,
        bitCoder.binToLongComparitor(guidesToSeekForBin.bin)).zip(guidesToSeekForBin.guides).foreach { case (ots,guide) => {

        siteSequenceToSite(guide).addOTs(ots)

        // if we're done with a guide, tell our traverser to remove it
        if (siteSequenceToSite(guide).full) {
          traversal.overflowGuide(guide)
          logger.debug("Guide " + bitCoder.bitDecodeString(guide).str + " has overflowed, and will no longer collect off-targets (set limit of " + siteSequenceToSite(guide).offTargets.result().size + ")")
        }
      }
      }

      binIndex += 1
      if (binIndex % 1000 == 0) {
        logger.info(formatter.format(binIndex) + " / " + formatter.format(traversal.traversalSize) + " bins; guides: " + guidesToSeekForBin.guides.size + "; targets: " + binPositionInformation.numberOfTargets + "; " + ((System.nanoTime() - t0) / 1000000000.0) +
          " seconds/1K bins, executed " + formatter.format(BitEncoding.allComparisons) + " comparisions")
        t0 = System.nanoTime()
      }

    }
    }

    siteSequenceToSite.values.toArray
  }

  /**
    * fill a block of off-targets from the database
    *
    * @param blockCompressedInput the block compressed stream to pull from
    * @param blockInformation information about the block we'd like to fetch
    * @param file             file name to use
    * @return
    */
  private def fillBlock(blockCompressedInput: BlockCompressedInputStream, blockInformation: BlockOffset, file: File, bin: String, bitCoder: BitEncoding): (Array[Long]) = {
    assert(blockInformation.uncompressedSize >= 0, "Bin sizes must be positive (or zero)")

    val readToBlock = new Array[Byte](blockInformation.uncompressedArraySize * 8)
    val read = blockCompressedInput.read(readToBlock)

    Utils.byteArrayToLong(readToBlock)
  }


}
