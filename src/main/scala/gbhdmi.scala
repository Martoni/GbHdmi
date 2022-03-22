package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.experimental.BundleLiterals._

import gbvga.{Gb, VgaColors, GbConst, GbWrite}
import hdmicore.{Tmds, RGBColors, VideoHdmi}
import hdmicore.{DiffPair, TMDSDiff, VideoHdmi, Tmds}
import hdmicore.{Rgb2Tmds}
import fpgamacro.gowin.{Oser10Module, TLVDS_OBUF}

class HdmiColors extends Bundle {
  val red   = UInt(8.W)
  val green = UInt(8.W)
  val blue  = UInt(8.W)
}

trait GbHdmiConst { self: RawModule =>
    def vga2hdmiColors(c: VgaColors): HdmiColors = {
        val retColors = Wire(new HdmiColors())
        retColors.red   := c.red ## "h0".U(2.W)
        retColors.green := c.green ## "h0".U(2.W)
        retColors.blue  := c.blue ## "h0".U(2.W)
        retColors
    }
}

class GbHdmi extends Module
            with GbConst with GbHdmiConst{
  val io = IO(new Bundle {
    /* Game boy input signals */
    val gb = Input(new Gb())
    /* fast serial x5 clock */
    val serClk = Input(Clock())
    /* HDMI output signals */
    val tmds = Output(new TMDSDiff())
    /* Pattern trigger */
    val pattern_trig = Input(Bool())
  })

  /* GameBoy write */
  val gbwt = Module(new GbWrite(2, debug_simu=false, aformal=false))
  gbwt.io.gb := io.gb

  /* Mem Vga */
  val mhdmi = Module(new MemHdmi())

  /* pattern selection */
  val patternNum = RegInit(0.U(2.W))
  when(!RegNext(io.pattern_trig) & io.pattern_trig){
    patternNum := patternNum + 1.U
  }
  mhdmi.io.pattern_num := patternNum

  val rgb2tmds = Module(new Rgb2Tmds())
  rgb2tmds.io.videoSig.de := mhdmi.io.video_de
  rgb2tmds.io.videoSig.hsync := mhdmi.io.video_hsync
  rgb2tmds.io.videoSig.vsync := mhdmi.io.video_vsync
  rgb2tmds.io.videoSig.pixel.red   := mhdmi.io.video_color.red
  rgb2tmds.io.videoSig.pixel.green := mhdmi.io.video_color.green
  rgb2tmds.io.videoSig.pixel.blue  := mhdmi.io.video_color.blue

  /* serdes */
  // Red -> data 2
  val serdesRed = Module(new Oser10Module())
  serdesRed.io.data := rgb2tmds.io.tmds_red
  serdesRed.io.fclk := io.serClk
  val buffDiffRed = Module(new TLVDS_OBUF())
  buffDiffRed.io.I := serdesRed.io.q
  io.tmds.data(2).p := buffDiffRed.io.O
  io.tmds.data(2).n := buffDiffRed.io.OB

  // Green -> data 1
  val serdesGreen = Module(new Oser10Module())
  serdesGreen.io.data := rgb2tmds.io.tmds_green
  serdesGreen.io.fclk := io.serClk
  val buffDiffGreen = Module(new TLVDS_OBUF())
  buffDiffGreen.io.I := serdesGreen.io.q
  io.tmds.data(1).p := buffDiffGreen.io.O
  io.tmds.data(1).n := buffDiffGreen.io.OB

  // Blue -> data 0
  val serdesBlue = Module(new Oser10Module())
  serdesBlue.io.data := rgb2tmds.io.tmds_blue
  serdesBlue.io.fclk := io.serClk
  val buffDiffBlue = Module(new TLVDS_OBUF())
  buffDiffBlue.io.I := serdesBlue.io.q
  io.tmds.data(0).p := buffDiffBlue.io.O
  io.tmds.data(0).n := buffDiffBlue.io.OB

  // clock
  val serdesClk = Module(new Oser10Module())
  serdesClk.io.data := "b1111100000".U(10.W)
  serdesClk.io.fclk := io.serClk
  val buffDiffClk = Module(new TLVDS_OBUF())
  buffDiffClk.io.I := serdesClk.io.q
  io.tmds.clk.p := buffDiffClk.io.O
  io.tmds.clk.n := buffDiffClk.io.OB

  /* dual port ram connection */
  val mem = Mem(GBWIDTH*GBHEIGHT, UInt(2.W))
  when(gbwt.io.Mwrite) {
    mem(gbwt.io.Maddr) := gbwt.io.Mdata
  }
  val last_read_value = RegInit(0.U(2.W))
  when(mhdmi.io.mem_read) {
    mhdmi.io.mem_data := RegNext(mem(mhdmi.io.mem_addr))
    last_read_value := mhdmi.io.mem_data
  }.otherwise {
    mhdmi.io.mem_data := last_read_value
  }
}

object GbHdmiDriver extends App {
  (new ChiselStage).execute(args,
    Seq(ChiselGeneratorAnnotation(() => new GbHdmi())))
}
