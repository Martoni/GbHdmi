package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

import gbvga.{Gb}
import fpgamacro.gowin.{CLKDIV, TMDS_PLLVR}

class TopGbHdmi(gowinDviTx: Boolean = true) extends RawModule {

    /************/
    /** outputs */
    /* Clock and reset */
    val I_clk = IO(Input(Clock()))
    val I_reset_n = IO(Input(Bool()))

    /* Debug leds */
    val O_led = IO(Output(UInt(2.W)))

    /* game boy signals */
    val gb = IO(Input(new Gb()))

    /* TMDS (HDMI) signals */
    val O_tmds_clk_p  = IO(Output(Bool()))
    val O_tmds_clk_n  = IO(Output(Bool()))
    val O_tmds_data_p = IO(Output(UInt(3.W)))
    val O_tmds_data_n = IO(Output(UInt(3.W)))
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

      /* synchronize gameboy input signals with clock */
      val shsync = ShiftRegister(gb.hsync,2)
      val svsync = ShiftRegister(gb.vsync,2)
      val sclk   = ShiftRegister(gb.clk  ,2)
      val sdata  = ShiftRegister(gb.data ,2)

      /* top GbVga module instantiation */
      val gbHdmi = Module(new GbHdmi(gowinDviTx))

      gbHdmi.io.gb.hsync := shsync
      gbHdmi.io.gb.vsync := svsync
      gbHdmi.io.gb.clk   := sclk
      gbHdmi.io.gb.data  := sdata

      /* counter debug */
      val max_count = 27000000
      val (counterReg, counterPulse) = Counter(true.B, max_count)
      O_led := (counterReg >= (max_count/2).U)

      gbHdmi.io.serClk := serial_clk

      O_tmds_clk_p  := gbHdmi.io.tmds.clk.p
      O_tmds_clk_n  := gbHdmi.io.tmds.clk.n
      O_tmds_data_p := 
        gbHdmi.io.tmds.data(2).p ## gbHdmi.io.tmds.data(1).p ## gbHdmi.io.tmds.data(0).p
      O_tmds_data_n :=
        gbHdmi.io.tmds.data(2).n ## gbHdmi.io.tmds.data(1).n ## gbHdmi.io.tmds.data(0).n
    }
}

object TopGbHdmiDriver extends App {
  var gowinDviTx = true
  for(arg <- args){
    if(arg == "noGowinDviTx")
      gowinDviTx = false 
  }
  if(gowinDviTx)
    println("Generate GbHdmi with encrypted Gowin DviTx core")
  else
    println("Generate GbHdmi with open source HdmiCore core")
  (new ChiselStage).execute(args,
    Seq(ChiselGeneratorAnnotation(() => new TopGbHdmi(gowinDviTx))))
}
