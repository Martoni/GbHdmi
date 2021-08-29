package gbhdmi

import chisel3._
import chisel3.util._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

import gbvga.{Gb}

class TopGbHdmi extends RawModule {

  /************/
  /** outputs */
  /* Clock and reset */
  val clock = IO(Input(Clock()))
  val resetn = IO(Input(Bool()))
  val pll_rstn = IO(Output(Bool()))

  /* game boy signals */
  val gb = IO(Input(new Gb()))

  /* TMDS (HDMI) signals */

  withClockAndReset(clock, ~resetn) {
    /* Activate pll at start*/
    pll_rstn := true.B
   
    /* synchronize gameboy input signals with clock */
    val shsync = ShiftRegister(gb.hsync,2)
    val svsync = ShiftRegister(gb.vsync,2)
    val sclk   = ShiftRegister(gb.clk  ,2)
    val sdata  = ShiftRegister(gb.data ,2)

//    /* top GbVga module instantiation */
//    val gb = Module(new GbVga())
//    gbVga.io.gb.hsync := shsync
//    gbVga.io.gb.vsync := svsync
//    gbVga.io.gb.clk   := sclk
//    gbVga.io.gb.data  := sdata
//
//    vga_hsync := gbVga.io.vga_hsync
//    vga_vsync := gbVga.io.vga_vsync
//    vga_color := gbVga.io.vga_color
  }
}


