package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

import gbvga.{Gb}
import chisnespad.ChisNesPad
import hdmicore.{TMDSDiff, VideoHdmi, Tmds}
import fpgamacro.gowin.{CLKDIV, TMDS_PLLVR}

class TopGbHdmi(gowinDviTx: Boolean = true) extends RawModule {

    val clockFreq = 1000;
    val mainClockFreq = 27000000;

    /********************************************/
    /**             inputs/outputs              */

    /* Clock and reset */
    val I_clk = IO(Input(Clock()))
    val I_reset_n = IO(Input(Bool()))

    /* Debug leds */
    val O_led = IO(Output(UInt(2.W)))

    /* game boy signals */
    val gb = IO(Input(new Gb()))

    /* TMDS (HDMI) signals */
    val O_tmds = IO(Output(new TMDSDiff()))

    /* snes pad io */
    val snes_dclock = IO(Output(Bool()))
    val snes_dlatch = IO(Output(Bool()))
    val snes_sdata = IO(Input(Bool()))
    
    /* gb pad output */
    val gbpad_a      = IO(Output(Bool()))
    val gbpad_b      = IO(Output(Bool()))
    val gbpad_select = IO(Output(Bool()))
    val gbpad_start  = IO(Output(Bool()))
    val gbpad_right  = IO(Output(Bool()))
    val gbpad_left   = IO(Output(Bool()))
    val gbpad_up     = IO(Output(Bool()))
    val gbpad_down   = IO(Output(Bool()))
    /********************************************/

    O_led := 1.U(2.W) 

    val pll_lock =  Wire(Bool())
    val serial_clk = Wire(Clock())
    val pix_clk = Wire(Clock())

    val glb_rst = ~(pll_lock & I_reset_n)

    /* CLKDIV */
    val clkDiv = Module(new CLKDIV())
    clkDiv.io.RESETN := ~glb_rst
    clkDiv.io.HCLKIN := serial_clk
    pix_clk := clkDiv.io.CLKOUT
    clkDiv.io.CALIB := true.B

    /* TMDS PLL */
    val tmdsPllvr = Module(new TMDS_PLLVR())
    tmdsPllvr.io.clkin := I_clk
    serial_clk := tmdsPllvr.io.clkout
    pll_lock := tmdsPllvr.io.lock

    withClockAndReset(pix_clk, glb_rst) {

      /* plug SuperNes pad */
      val cnpd = Module(new ChisNesPad(mainClockFreq, clockFreq, 16))
      val sNesPadReg = RegInit(0.U(16.W))
      /* input shift reg ctrl */
      snes_dclock := cnpd.io.dclock
      snes_dlatch := cnpd.io.dlatch
      cnpd.io.sdata := snes_sdata
      /* output register */
      cnpd.io.data.ready := true.B
      when(cnpd.io.data.valid){
        sNesPadReg := ~cnpd.io.data.bits
      }
      gbpad_b      := sNesPadReg(15)
      //pad_y      := sNesPadReg(14)
      gbpad_select := sNesPadReg(13)
      gbpad_start  := sNesPadReg(12)
      gbpad_up     := sNesPadReg(11)
      gbpad_down   := sNesPadReg(10)
      gbpad_left   := sNesPadReg(9)
      gbpad_right  := sNesPadReg(8)
      gbpad_a      := sNesPadReg(7)
      //pad_x      := sNesPadReg(6)
      //pad_l      := sNesPadReg(5)
      //pad_r      := sNesPadReg(4)
      O_led := sNesPadReg(11) ## sNesPadReg(10)

      /* synchronize gameboy input signals with clock */
      val shsync = ShiftRegister(gb.hsync,2)
      val svsync = ShiftRegister(gb.vsync,2)
      val sclk   = ShiftRegister(gb.clk  ,2)
      val sdata  = ShiftRegister(gb.data ,2)

      /* top GbVga module instantiation */
      val gbHdmi = Module(new GbHdmi())

      gbHdmi.io.pattern_trig := sNesPadReg(4) | sNesPadReg(5)

      gbHdmi.io.gb.hsync := shsync
      gbHdmi.io.gb.vsync := svsync
      gbHdmi.io.gb.clk   := sclk
      gbHdmi.io.gb.data  := sdata

      gbHdmi.io.serClk := serial_clk

      O_tmds.clk.p :=  gbHdmi.io.tmds.clk.p
      O_tmds.clk.n  := gbHdmi.io.tmds.clk.n
      O_tmds.data(0).p := gbHdmi.io.tmds.data(0).p
      O_tmds.data(0).n := gbHdmi.io.tmds.data(0).n
      O_tmds.data(1).p := gbHdmi.io.tmds.data(1).p
      O_tmds.data(1).n := gbHdmi.io.tmds.data(1).n
      O_tmds.data(2).p := gbHdmi.io.tmds.data(2).p
      O_tmds.data(2).n := gbHdmi.io.tmds.data(2).n
    }
}

object TopGbHdmiDriver extends App {
  println("Generate GbHdmi with open source HdmiCore core")
  (new ChiselStage).execute(args,
    Seq(ChiselGeneratorAnnotation(() => new TopGbHdmi())))
}
