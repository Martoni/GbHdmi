package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.experimental.BundleLiterals._

import gbvga.{Gb, VgaColors, GbConst, GbWrite}
import hdmicore.{Tmds, RGBColors, VideoHdmi}
import hdmicore.{Rgb2Tmds}
import fpgamacro.gowin.{Oser10Module, TLVDS_OBUF}

class HdmiColors extends Bundle {
  val red   = UInt(8.W)
  val green = UInt(8.W)
  val blue  = UInt(8.W)
}

class DiffPair extends Bundle {
    val p = Bool()
    val n = Bool()
}

class TMDSDiff extends Bundle {
    val clk  = new DiffPair()
    val data = Vec(3, new DiffPair())
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

/* Use encrypted gowinDviTx by default */
class GbHdmi(gowinDviTx: Boolean = true) extends Module
            with GbConst with GbHdmiConst{
  val io = IO(new Bundle {
    /* Game boy input signals */
    val gb = Input(new Gb())
    /* fast serial x5 clock */
    val serClk = Input(Clock())
    /* HDMI output signals */
    val tmds = Output(new TMDSDiff())
  })

  /* GameBoy write */
  val gbwt = Module(new GbWrite(2, debug_simu=false, aformal=false))
  gbwt.io.gb := io.gb

  /* Mem Vga */
  val mhdmi = Module(new MemHdmi())

  /* HDMI interface */
  if(gowinDviTx){
    val dvitxtop = Module(new DVI_TX_Top())

    /* Clocks and reset */
    dvitxtop.io.I_rst_n := RegNext(true.B, false.B)
    dvitxtop.io.I_serial_clk := io.serClk
    dvitxtop.io.I_rgb_clk := clock

    /* video signals connexions */
    dvitxtop.io.I_rgb_vs := mhdmi.io.video_vsync
    dvitxtop.io.I_rgb_hs := mhdmi.io.video_hsync
    dvitxtop.io.I_rgb_de := mhdmi.io.video_de
    dvitxtop.io.I_rgb_r := mhdmi.io.video_color.red
    dvitxtop.io.I_rgb_g := mhdmi.io.video_color.green
    dvitxtop.io.I_rgb_b := mhdmi.io.video_color.blue
    /* tmds connexions */
    io.tmds.clk.p := dvitxtop.io.O_tmds_clk_p
    io.tmds.clk.n := dvitxtop.io.O_tmds_clk_n
    for(i <- 0 to 2) {
      io.tmds.data(i).p := dvitxtop.io.O_tmds_data_p(i)
      io.tmds.data(i).n := dvitxtop.io.O_tmds_data_n(i)
    }
  } else {
    val rgb2tmds = Module(new Rgb2Tmds())
    rgb2tmds.io.videoSig.de := mhdmi.io.video_de
    rgb2tmds.io.videoSig.hsync := mhdmi.io.video_hsync
    rgb2tmds.io.videoSig.vsync := mhdmi.io.video_vsync
    rgb2tmds.io.videoSig.pixel.red   := mhdmi.io.video_color.red
    rgb2tmds.io.videoSig.pixel.green := mhdmi.io.video_color.green
    rgb2tmds.io.videoSig.pixel.blue  := mhdmi.io.video_color.blue

    /* serdes */
    // Blue -> data 0
    val serdesBlue = Module(new Oser10Module())
    serdesBlue.io.data := rgb2tmds.io.tmds_blue
    serdesBlue.io.fclk := io.serClk
    val buffDiffBlue = Module(new TLVDS_OBUF())
    buffDiffBlue.io.I := serdesBlue.io.q
    io.tmds.data(0).p := buffDiffBlue.io.O
    io.tmds.data(0).n := buffDiffBlue.io.OB

    // Green -> data 1
    val serdesGreen = Module(new Oser10Module())
    serdesGreen.io.data := rgb2tmds.io.tmds_green
    serdesGreen.io.fclk := io.serClk
    val buffDiffGreen = Module(new TLVDS_OBUF())
    buffDiffGreen.io.I := serdesGreen.io.q
    io.tmds.data(1).p := buffDiffGreen.io.O
    io.tmds.data(1).n := buffDiffGreen.io.OB

    // Red -> data 2
    val serdesRed = Module(new Oser10Module())
    serdesRed.io.data := rgb2tmds.io.tmds_red
    serdesRed.io.fclk := io.serClk
    val buffDiffRed = Module(new TLVDS_OBUF())
    buffDiffRed.io.I := serdesRed.io.q
    io.tmds.data(2).p := buffDiffRed.io.O
    io.tmds.data(2).n := buffDiffRed.io.OB

    // clock
    val serdesClk = Module(new Oser10Module())
    serdesClk.io.data := "b1111100000".U(10.W)
    serdesClk.io.fclk := io.serClk
    val buffDiffClk = Module(new TLVDS_OBUF())
    buffDiffClk.io.I := serdesClk.io.q
    io.tmds.clk.p := buffDiffClk.io.O
    io.tmds.clk.n := buffDiffClk.io.OB
  }

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
