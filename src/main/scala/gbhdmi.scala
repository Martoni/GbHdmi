package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.experimental.BundleLiterals._

import gbvga.{Gb, VgaColors, GbConst, GbWrite}

class HdmiColors extends Bundle {
  val red   = UInt(8.W)
  val green = UInt(8.W)
  val blue  = UInt(8.W)
}

class DiffPair extends Bundle {
    val p = Bool()
    val n = Bool()
}

class TMDS extends Bundle {
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

class GbHdmi extends Module with GbConst with GbHdmiConst{
  val io = IO(new Bundle {
    /* Game boy input signals */
    val gb = Input(new Gb())
    /* fast serial x5 clock */
    val serClk = Input(Clock())
    /* HDMI output signals */
    val tmds = Output(new TMDS())
  })


  /* GameBoy write */
  val gbwt = Module(new GbWrite(2, debug_simu=false, aformal=false))
  gbwt.io.gb := io.gb

  /* Mem Vga */
  val mhdmi = Module(new MemHdmi())

  /* HDMI interface */
  val dvitxtop = Module(new DVI_TX_Top())
  dvitxtop.io.I_rst_n := RegNext(true.B, false.B)
  dvitxtop.io.I_serial_clk := io.serClk
  dvitxtop.io.I_rgb_clk := clock
  dvitxtop.io.I_rgb_vs := mhdmi.io.video_vsync
  dvitxtop.io.I_rgb_hs := mhdmi.io.video_hsync
  dvitxtop.io.I_rgb_de := mhdmi.io.video_de 
  dvitxtop.io.I_rgb_r := mhdmi.io.video_color.red
  dvitxtop.io.I_rgb_g := mhdmi.io.video_color.green
  dvitxtop.io.I_rgb_b := mhdmi.io.video_color.blue
  io.tmds.clk.p := dvitxtop.io.O_tmds_clk_p  
  io.tmds.clk.n := dvitxtop.io.O_tmds_clk_n
  for(i <- 0 to 2) {
    io.tmds.data(i).p := dvitxtop.io.O_tmds_data_p(i)
    io.tmds.data(i).n := dvitxtop.io.O_tmds_data_n(i)
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
