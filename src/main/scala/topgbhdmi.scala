package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

import gbvga.{Gb}

class TopGbHdmi extends RawModule {

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

    val pll_lock =  Wire(Bool())
      val serial_clk = Wire(Clock())
    val pix_clk = Wire(Clock())

    val glb_rst = ~(pll_lock & I_reset_n)

    /* CLKDIV */
    val clkDiv = Module(new CLKDIV())
    clkDiv.io.RESETN := I_reset_n & pll_lock
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

//        /* top GbVga module instantiation */
        val gbHdmi = Module(new GbHdmi())

        gbHdmi.io.gb.hsync := shsync
        gbHdmi.io.gb.vsync := svsync
        gbHdmi.io.gb.clk   := sclk
        gbHdmi.io.gb.data  := sdata

        gbHdmi.io.serClk := serial_clk

        O_tmds_clk_p  := gbHdmi.io.tmds.clk.p
        O_tmds_clk_n  := gbHdmi.io.tmds.clk.n
        for(i <- 0 to 2){
            O_tmds_data_p(i) := gbHdmi.io.tmds.data(i).p
            O_tmds_data_n(i) := gbHdmi.io.tmds.data(i).n
        }
      }
}

object TopGbHdmiDriver extends App {
  (new ChiselStage).execute(args,
    Seq(ChiselGeneratorAnnotation(() => new TopGbHdmi())))
}
